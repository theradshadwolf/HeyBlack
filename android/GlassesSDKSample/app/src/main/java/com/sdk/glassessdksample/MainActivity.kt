package com.sdk.glassessdksample

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.XXPermissions
import com.oudmon.ble.base.bluetooth.BleOperateManager
import com.oudmon.ble.base.bluetooth.DeviceManager
import com.oudmon.ble.base.communication.LargeDataHandler
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyListener
import com.oudmon.ble.base.communication.bigData.resp.GlassesDeviceNotifyRsp
import com.sdk.glassessdksample.databinding.AcitivytMainBinding
import com.sdk.glassessdksample.ui.BluetoothUtils
import com.sdk.glassessdksample.ui.DeviceBindActivity
import com.sdk.glassessdksample.ui.hasBluetooth
import com.sdk.glassessdksample.ui.requestAllPermission
import com.sdk.glassessdksample.ui.requestBluetoothPermission
import com.sdk.glassessdksample.ui.requestLocationPermission
import com.sdk.glassessdksample.ui.requestNearbyWifiDevicesPermission
import com.sdk.glassessdksample.ui.setOnClickListener
import com.sdk.glassessdksample.ui.startKtxActivity
import com.sdk.glassessdksample.ui.wifi.p2p.WifiP2pManagerSingleton
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class MainActivity : AppCompatActivity() {
    private lateinit var binding: AcitivytMainBinding
    private val deviceNotifyListener by lazy { MyDeviceNotifyListener() }

    // Tracks active receiver so we can unregister it after download completes
    private var p2pReceiver: android.content.BroadcastReceiver? = null
    private var p2pManager: WifiP2pManagerSingleton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AcitivytMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    inner class PermissionCallback : OnPermissionCallback {
        override fun onGranted(permissions: MutableList<String>, all: Boolean) {
            if (all) startKtxActivity<DeviceBindActivity>()
        }

        override fun onDenied(permissions: MutableList<String>, never: Boolean) {
            super.onDenied(permissions, never)
            if (never) XXPermissions.startPermissionActivity(this@MainActivity, permissions)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            if (!BluetoothUtils.isEnabledBluetooth(this)) {
                val intent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(
                            this,
                            Manifest.permission.BLUETOOTH_CONNECT
                        ) != PackageManager.PERMISSION_GRANTED
                    ) return
                }
                @Suppress("DEPRECATION")
                startActivityForResult(intent, 300)
            }
        } catch (e: Exception) { }

        if (!hasBluetooth(this)) {
            requestBluetoothPermission(this, BluetoothPermissionCallback())
        }
        requestAllPermission(this, OnPermissionCallback { _, _ -> })
    }

    inner class BluetoothPermissionCallback : OnPermissionCallback {
        override fun onGranted(permissions: MutableList<String>, all: Boolean) { }

        override fun onDenied(permissions: MutableList<String>, never: Boolean) {
            super.onDenied(permissions, never)
            if (never) XXPermissions.startPermissionActivity(this@MainActivity, permissions)
        }
    }

    private fun initView() {
        setOnClickListener(
            binding.btnScan,
            binding.btnConnect,
            binding.btnDisconnect,
            binding.btnAddListener,
            binding.btnSetTime,
            binding.btnVersion,
            binding.btnCamera,
            binding.btnVideo,
            binding.btnRecord,
            binding.btnThumbnail,
            binding.btnBt,
            binding.btnBattery,
            binding.btnVolume,
            binding.btnMediaCount,
            binding.btnDataDownload
        ) {
            when (this) {
                binding.btnScan -> requestLocationPermission(this@MainActivity, PermissionCallback())

                binding.btnConnect -> BleOperateManager.getInstance()
                    .connectDirectly(DeviceManager.getInstance().deviceAddress)

                binding.btnDisconnect -> BleOperateManager.getInstance().unBindDevice()

                binding.btnAddListener -> LargeDataHandler.getInstance()
                    .addOutDeviceListener(100, deviceNotifyListener)

                binding.btnSetTime -> {
                    Log.i("setTime", "setTime" + BleOperateManager.getInstance().isConnected)
                    LargeDataHandler.getInstance().syncTime { _, _ -> }
                }

                binding.btnVersion -> {
                    LargeDataHandler.getInstance().syncDeviceInfo { _, response ->
                        if (response != null) {
                            response.wifiFirmwareVersion
                            response.wifiHardwareVersion
                            response.hardwareVersion
                            response.firmwareVersion
                        }
                    }
                }

                binding.btnCamera -> {
                    LargeDataHandler.getInstance().glassesControl(
                        byteArrayOf(0x02, 0x01, 0x01)
                    ) { _, it ->
                        if (it.dataType == 1 && it.errorCode == 0) {
                            when (it.workTypeIng) {
                                2 -> { }
                                4 -> { }
                                5 -> { }
                                1, 6 -> { }
                                7 -> { }
                                8 -> { }
                            }
                        }
                    }
                }

                binding.btnVideo -> {
                    val videoStart = true
                    val value = if (videoStart) 0x02 else 0x03
                    LargeDataHandler.getInstance().glassesControl(
                        byteArrayOf(0x02, 0x01, value.toByte())
                    ) { _, it ->
                        if (it.dataType == 1 && it.errorCode == 0) {
                            when (it.workTypeIng) {
                                2 -> { }
                                4 -> { }
                                5 -> { }
                                1, 6 -> { }
                                7 -> { }
                                8 -> { }
                            }
                        }
                    }
                }

                binding.btnRecord -> {
                    val recordStart = true
                    val value = if (recordStart) 0x08 else 0x0c
                    LargeDataHandler.getInstance().glassesControl(
                        byteArrayOf(0x02, 0x01, value.toByte())
                    ) { _, it ->
                        if (it.dataType == 1 && it.errorCode == 0) {
                            when (it.workTypeIng) {
                                2 -> { }
                                4 -> { }
                                5 -> { }
                                1, 6 -> { }
                                7 -> { }
                                8 -> { }
                            }
                        }
                    }
                }

                binding.btnThumbnail -> {
                    val thumbnailSize = 0x02
                    LargeDataHandler.getInstance().glassesControl(
                        byteArrayOf(0x02, 0x01, 0x06,
                            thumbnailSize.toByte(), thumbnailSize.toByte(), 0x02)
                    ) { _, it ->
                        if (it.dataType == 1 && it.errorCode == 0) {
                            when (it.workTypeIng) {
                                2 -> { }
                                4 -> { }
                                5 -> { }
                                1, 6 -> { }
                                7 -> { }
                                8 -> { }
                            }
                        }
                    }
                }

                binding.btnBt -> BleOperateManager.getInstance().classicBluetoothStartScan()

                binding.btnBattery -> {
                    LargeDataHandler.getInstance().addBatteryCallBack("init") { _, _ -> }
                    LargeDataHandler.getInstance().syncBattery()
                }

                binding.btnVolume -> {
                    LargeDataHandler.getInstance().getVolumeControl { _, response ->
                        if (response != null) {
                            response.minVolumeMusic
                            response.maxVolumeMusic
                            response.currVolumeMusic
                            response.minVolumeCall
                            response.maxVolumeCall
                            response.currVolumeCall
                            response.minVolumeSystem
                            response.maxVolumeSystem
                            response.currVolumeSystem
                            response.currVolumeType
                        }
                    }
                }

                binding.btnMediaCount -> {
                    LargeDataHandler.getInstance()
                        .glassesControl(byteArrayOf(0x02, 0x04)) { _, it ->
                            if (it.dataType == 4) {
                                val mediaCount = it.imageCount + it.videoCount + it.recordCount
                                Log.i("MediaCount", "Total media: $mediaCount")
                            }
                        }
                }

                binding.btnDataDownload -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNearbyWifiDevicesPermission(
                            this@MainActivity,
                            object : OnPermissionCallback {
                                override fun onGranted(
                                    permissions: MutableList<String>,
                                    all: Boolean
                                ) {
                                    if (all) startDataDownload()
                                }

                                override fun onDenied(
                                    permissions: MutableList<String>,
                                    never: Boolean
                                ) {
                                    super.onDenied(permissions, never)
                                    if (never) XXPermissions.startPermissionActivity(
                                        this@MainActivity, permissions
                                    )
                                }
                            }
                        )
                    } else {
                        startDataDownload()
                    }
                }
            }
        }
    }

    private fun startDataDownload() {
        Log.i("DataDownload", "Starting BLE+WiFi P2P data download...")

        if (!BleOperateManager.getInstance().isConnected) {
            Log.e("DataDownload", "Bluetooth not connected. Please connect to glasses first.")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!XXPermissions.isGranted(this, "android.permission.NEARBY_WIFI_DEVICES")) {
                Log.e("DataDownload", "NEARBY_WIFI_DEVICES permission not granted")
                return
            }
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceIp = getDeviceIpFromBLE()
                if (deviceIp.isNullOrEmpty()) {
                    Log.e("DataDownload", "Failed to get device IP from BLE")
                    return@launch
                }

                Log.i("DataDownload", "Device IP from BLE: $deviceIp")

                val wifiP2pMgr = WifiP2pManagerSingleton.getInstance(this@MainActivity)
                p2pManager = wifiP2pMgr
                val receiver = wifiP2pMgr.registerReceiver()
                p2pReceiver = receiver

                wifiP2pMgr.addCallback(object : WifiP2pManagerSingleton.WifiP2pCallback {
                    override fun onWifiP2pEnabled() {
                        Log.i("DataDownload", "WiFi P2P enabled, creating P2P group...")
                        wifiP2pMgr.createGroup { success ->
                            if (success) {
                                Log.i("DataDownload", "P2P group created successfully")
                                CoroutineScope(Dispatchers.IO).launch {
                                    delay(2000)
                                    if (testConnection(deviceIp)) {
                                        Log.i("DataDownload", "Connection OK, starting downloads...")
                                        downloadMediaList(deviceIp)
                                    } else {
                                        Log.e("DataDownload", "Connection test failed")
                                        withContext(Dispatchers.Main) {
                                            showDownloadError("Cannot connect to glasses device.")
                                        }
                                        teardownP2p(wifiP2pMgr, receiver)
                                    }
                                }
                            } else {
                                Log.e("DataDownload", "Failed to create P2P group")
                                CoroutineScope(Dispatchers.Main).launch {
                                    showDownloadError("Failed to create P2P group")
                                }
                                teardownP2p(wifiP2pMgr, receiver)
                            }
                        }
                    }

                    override fun onWifiP2pDisabled() {
                        Log.e("DataDownload", "WiFi P2P disabled")
                        teardownP2p(wifiP2pMgr, receiver)
                    }

                    override fun onPeersChanged(peers: Collection<WifiP2pDevice>) {
                        Log.i("DataDownload", "Found ${peers.size} P2P devices")
                    }

                    override fun onThisDeviceChanged(device: WifiP2pDevice) {
                        Log.i("DataDownload", "This device: ${device.deviceName} - ${device.status}")
                    }

                    override fun onConnected(info: WifiP2pInfo) {
                        Log.i("DataDownload", "P2P connected: groupFormed=${info.groupFormed}")
                    }

                    override fun onDisconnected() {
                        Log.i("DataDownload", "P2P disconnected")
                        teardownP2p(wifiP2pMgr, receiver)
                    }

                    override fun onPeerDiscoveryStarted() {}
                    override fun onPeerDiscoveryFailed(reason: Int) {
                        Log.e("DataDownload", "Peer discovery failed: $reason")
                    }
                    override fun onConnectRequestSent() {}
                    override fun onConnectRequestFailed(reason: Int) {
                        Log.e("DataDownload", "Connect request failed: $reason")
                    }
                    override fun connecting() {}
                    override fun cancelConnect() {}
                    override fun cancelConnectFail(reason: Int) {}
                    override fun retryAlsoFailed() {
                        Log.e("DataDownload", "P2P retry also failed")
                        teardownP2p(wifiP2pMgr, receiver)
                    }
                })

            } catch (e: Exception) {
                Log.e("DataDownload", "Error during data download: ${e.message}", e)
            }
        }
    }

    /** Tear down P2P group and unregister receiver — called after download ends, not before. */
    private fun teardownP2p(
        mgr: WifiP2pManagerSingleton,
        receiver: android.content.BroadcastReceiver
    ) {
        mgr.removeGroup { success ->
            Log.i("DataDownload", "P2P group removed: $success")
        }
        mgr.unregisterReceiver(receiver)
        p2pReceiver = null
        p2pManager = null
    }

    private fun getDeviceIpFromBLE(): String? {
        return "192.168.49.79"
    }

    private suspend fun downloadMediaList(deviceIp: String) {
        try {
            val url = "http://$deviceIp/files/media.config"
            Log.i("DataDownload", "Downloading media list from: $url")

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 30000

            try {
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.i("DataDownload", "=== MEDIA CONFIG ===\n$content\n=== END ===")
                    parseMediaList(content, deviceIp)
                    withContext(Dispatchers.Main) {
                        showDownloadSuccess("Media list downloaded successfully")
                    }
                } else {
                    Log.e("DataDownload", "Bad response: ${connection.responseCode}")
                    withContext(Dispatchers.Main) {
                        showDownloadError("Failed to download media list: ${connection.responseCode}")
                    }
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e("DataDownload", "Error downloading media list: ${e.message}", e)
            withContext(Dispatchers.Main) {
                when {
                    e.message?.contains("Cleartext HTTP traffic") == true ->
                        showDownloadError("Network security blocked HTTP. Check network_security_config.")
                    e.message?.contains("Failed to connect") == true ->
                        showDownloadError("Cannot connect to glasses. Ensure P2P is established.")
                    else -> showDownloadError("Network error: ${e.message}")
                }
            }
        }
    }

    private suspend fun parseMediaList(content: String, deviceIp: String) {
        Log.i("DataDownload", "Parsing media list...")
        try {
            val jpgFiles = content.trim().split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filter {
                    it.endsWith(".jpg", ignoreCase = true) ||
                        it.endsWith(".jpeg", ignoreCase = true)
                }

            Log.i("DataDownload", "JPG files found: ${jpgFiles.size}")

            if (jpgFiles.isNotEmpty()) {
                downloadAllJpgFiles(jpgFiles, deviceIp)
            } else {
                Log.w("DataDownload", "No JPG files in media.config")
                withContext(Dispatchers.Main) {
                    showDownloadError("No JPG files found in media.config")
                }
            }
        } catch (e: Exception) {
            Log.e("DataDownload", "Error parsing media list: ${e.message}", e)
            withContext(Dispatchers.Main) {
                showDownloadError("Failed to parse media list: ${e.message}")
            }
        }
    }

    private suspend fun downloadAllJpgFiles(jpgFiles: List<String>, deviceIp: String) {
        Log.i("DataDownload", "Downloading ${jpgFiles.size} JPG files...")
        var successCount = 0
        var failCount = 0

        for ((index, fileName) in jpgFiles.withIndex()) {
            try {
                Log.i("DataDownload", "File ${index + 1}/${jpgFiles.size}: $fileName")
                if (downloadSingleJpgFile(fileName, deviceIp)) {
                    successCount++
                    Log.i("DataDownload", "\u2713 $fileName")
                } else {
                    failCount++
                    Log.e("DataDownload", "\u2717 $fileName")
                }
                delay(500)
            } catch (e: Exception) {
                failCount++
                Log.e("DataDownload", "Error: $fileName — ${e.message}", e)
            }
        }

        Log.i("DataDownload", "Done: $successCount ok, $failCount failed")
        withContext(Dispatchers.Main) {
            if (failCount == 0) showDownloadSuccess("All $successCount files downloaded!")
            else showDownloadError("$successCount ok, $failCount failed")
        }
    }

    private suspend fun downloadSingleJpgFile(fileName: String, deviceIp: String): Boolean {
        return try {
            val url = "http://$deviceIp/files/$fileName"
            Log.i("DataDownload", "GET $url")

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 30000

            try {
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    // Null-safe external storage dir
                    val dir = getExternalFilesDir("DCIM")
                        ?: filesDir  // fallback to internal storage if external unavailable
                    val file = File(dir, fileName)
                    FileOutputStream(file).use { out ->
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(8192)
                            var totalBytes = 0L
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                                totalBytes += bytesRead
                            }
                            Log.i("DataDownload", "Saved $fileName ($totalBytes bytes) -> ${file.absolutePath}")
                        }
                    }
                    saveToAlbum(file, fileName)
                    true
                } else {
                    Log.e("DataDownload", "HTTP ${connection.responseCode} for $fileName")
                    false
                }
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e("DataDownload", "Error downloading $fileName: ${e.message}", e)
            false
        }
    }

    private fun saveToAlbum(file: File, fileName: String) {
        try {
            val albumInfo = mapOf(
                "fileName" to fileName,
                "filePath" to file.absolutePath,
                "fileDate" to "2025-08-18",
                "fileType" to 1,
                "timestamp" to System.currentTimeMillis(),
                "mac" to "71:33:1D:2C:CF:A0"
            )
            Log.i("DataDownload", "Album info: $albumInfo")
        } catch (e: Exception) {
            Log.e("DataDownload", "Error saving to album: ${e.message}", e)
        }
    }

    private fun showDownloadSuccess(message: String) {
        Log.i("DataDownload", "SUCCESS: $message")
    }

    private fun showDownloadError(message: String) {
        Log.e("DataDownload", "ERROR: $message")
    }

    private suspend fun testConnection(deviceIp: String): Boolean {
        return try {
            Log.i("DataDownload", "Testing connection to $deviceIp...")
            val connection = URL("http://$deviceIp/files/media.config")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            try {
                val responseCode = connection.responseCode
                Log.i("DataDownload", "Connection test: HTTP $responseCode")
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.use { it.read(ByteArray(1024)) }
                    true
                } else false
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            Log.e("DataDownload", "Connection test failed: ${e.message}", e)
            false
        }
    }

    inner class MyDeviceNotifyListener : GlassesDeviceNotifyListener() {

        @RequiresApi(Build.VERSION_CODES.O)
        override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
            when (response.loadData[6].toInt()) {
                0x05 -> {
                    val battery = response.loadData[7].toInt()
                    val changing = response.loadData[8].toInt()
                    Log.d("Notify", "Battery: $battery, charging: $changing")
                }
                0x02 -> {
                    if (response.loadData.size > 9 &&
                        response.loadData[9].toInt() == 0x02) { }
                    LargeDataHandler.getInstance()
                        .getPictureThumbnails { _, _, _ -> }
                }
                0x03 -> { if (response.loadData[7].toInt() == 1) { } }
                0x04 -> {
                    try {
                        val download = response.loadData[7].toInt()
                        val soc = response.loadData[8].toInt()
                        val nor = response.loadData[9].toInt()
                        Log.d("Notify", "OTA: dl=$download soc=$soc nor=$nor")
                    } catch (e: Exception) { e.printStackTrace() }
                }
                0x0c -> { if (response.loadData[7].toInt() == 1) { } }
                0x0d -> { if (response.loadData[7].toInt() == 1) { } }
                0x0e -> { }
                0x10 -> { }
                0x12 -> {
                    val vs = listOf(8, 9, 10, 12, 13, 14, 16, 17, 18, 19)
                        .map { response.loadData[it].toInt() }
                    Log.d("Notify", "0x12 values: $vs")
                }
            }
        }
    }
}

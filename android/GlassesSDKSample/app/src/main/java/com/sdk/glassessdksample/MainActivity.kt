package com.sdk.glassessdksample

import android.Manifest
import android.app.Activity
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
import com.sdk.glassessdksample.ui.P2PController
import com.sdk.glassessdksample.ui.wifi.p2p.WifiP2pManagerSingleton
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import org.greenrobot.eventbus.EventBus
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AcitivytMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }
    inner class PermissionCallback : OnPermissionCallback {
        override fun onGranted(permissions: MutableList<String>, all: Boolean) {
            if (!all) {

            }else{
                startKtxActivity<DeviceBindActivity>()
            }
        }

        override fun onDenied(permissions: MutableList<String>, never: Boolean) {
            super.onDenied(permissions, never)
            if(never){
                XXPermissions.startPermissionActivity(this@MainActivity, permissions);
            }
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
                    ) {
                        return
                    }
                }
                startActivityForResult(intent, 300)
            }
        } catch (e: Exception) {
        }
        if (!hasBluetooth(this)) {
            requestBluetoothPermission(this, BluetoothPermissionCallback())
        }

        requestAllPermission(this, OnPermissionCallback { permissions, all ->  })
    }

    inner class BluetoothPermissionCallback : OnPermissionCallback {
        override fun onGranted(permissions: MutableList<String>, all: Boolean) {
            if (!all) {

            }
        }

        override fun onDenied(permissions: MutableList<String>, never: Boolean) {
            super.onDenied(permissions, never)
            if (never) {
                XXPermissions.startPermissionActivity(this@MainActivity, permissions)
            }
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
                binding.btnScan -> {
                    requestLocationPermission(this@MainActivity, PermissionCallback())
                }

                binding.btnConnect -> {
                    BleOperateManager.getInstance()
                        .connectDirectly(DeviceManager.getInstance().deviceAddress)
                }

                binding.btnDisconnect -> {
                    BleOperateManager.getInstance().unBindDevice()
                }

                binding.btnAddListener -> {
                    LargeDataHandler.getInstance().addOutDeviceListener(100, deviceNotifyListener)
                }

                binding.btnSetTime -> {
                    Log.i("setTime", "setTime"+BleOperateManager.getInstance().isConnected)
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
                                1, 6 ->{ }
                                7 -> { }
                                8 ->{ }
                            }
                        } else { }
                    }
                }

                binding.btnVideo -> {
                    val videoStart=true
                    val value = if (videoStart) 0x02 else 0x03
                    LargeDataHandler.getInstance().glassesControl(
                        byteArrayOf(0x02, 0x01, value.toByte())
                    ) { _, it ->
                        if (it.dataType == 1) {
                            if (it.errorCode == 0) {
                                when (it.workTypeIng) {
                                    2 -> { }
                                    4 -> { }
                                    5 -> { }
                                    1, 6 ->{ }
                                    7 -> { }
                                    8 ->{ }
                                }
                            } else { }
                        }
                    }
                }

                binding.btnRecord -> {
                    val recordStart=true
                    val value = if (recordStart) 0x08 else 0x0c
                    LargeDataHandler.getInstance().glassesControl(
                        byteArrayOf(0x02, 0x01, value.toByte())
                    ) { _, it ->
                        if (it.dataType == 1) {
                            if (it.errorCode == 0) {
                                when (it.workTypeIng) {
                                    2 -> { }
                                    4 -> { }
                                    5 -> { }
                                    1, 6 ->{ }
                                    7 -> { }
                                    8 ->{ }
                                }
                            } else { }
                        }
                    }
                }

                binding.btnThumbnail -> {
                    val thumbnailSize=0x02
                    LargeDataHandler.getInstance().glassesControl(
                        byteArrayOf(
                            0x02,
                            0x01,
                            0x06,
                            thumbnailSize.toByte(),
                            thumbnailSize.toByte(),
                            0x02
                        )
                    ) { _, it ->
                        if (it.dataType == 1) {
                            if (it.errorCode == 0) {
                                when (it.workTypeIng) {
                                    2 -> { }
                                    4 -> { }
                                    5 -> { }
                                    1, 6 ->{ }
                                    7 -> { }
                                    8 ->{ }
                                }
                            } else { }
                        }
                    }
                }

                binding.btnBt -> {
                    BleOperateManager.getInstance().classicBluetoothStartScan()
                }
                binding.btnBattery -> {
                    LargeDataHandler.getInstance().addBatteryCallBack("init") { _, response -> }
                    LargeDataHandler.getInstance().syncBattery()
                }
                binding.btnVolume ->{
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
                binding.btnMediaCount ->{
                    LargeDataHandler.getInstance().glassesControl(byteArrayOf(0x02, 0x04)) { _, it ->
                        if (it.dataType == 4) {
                            val mediaCount = it.imageCount + it.videoCount + it.recordCount
                            if (mediaCount > 0) { } else { }
                        }
                    }
                }
                binding.btnDataDownload -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        requestNearbyWifiDevicesPermission(this@MainActivity, object : OnPermissionCallback {
                            override fun onGranted(permissions: MutableList<String>, all: Boolean) {
                                if (all) {
                                    startDataDownload()
                                }
                            }

                            override fun onDenied(permissions: MutableList<String>, never: Boolean) {
                                super.onDenied(permissions, never)
                                if (never) {
                                    XXPermissions.startPermissionActivity(this@MainActivity, permissions)
                                }
                            }
                        })
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

                val wifiP2pManager = WifiP2pManagerSingleton.getInstance(this@MainActivity)
                val receiver = wifiP2pManager.registerReceiver()

                try {
                    wifiP2pManager.addCallback(object : WifiP2pManagerSingleton.WifiP2pCallback {
                        override fun onWifiP2pEnabled() {
                            Log.i("DataDownload", "WiFi P2P enabled, creating P2P group...")
                            wifiP2pManager.createGroup { success ->
                                if (success) {
                                    Log.i("DataDownload", "P2P group created successfully")
                                    // Fix: wrap coroutine calls inside a new coroutine scope
                                    CoroutineScope(Dispatchers.IO).launch {
                                        delay(2000)

                                        if (testConnection(deviceIp)) {
                                            Log.i("DataDownload", "Connection test successful, starting downloads...")
                                            downloadMediaList(deviceIp)
                                        } else {
                                            Log.e("DataDownload", "Connection test failed, cannot reach device")
                                            withContext(Dispatchers.Main) {
                                                showDownloadError("Cannot connect to glasses device. Please check P2P connection.")
                                            }
                                        }
                                    }
                                } else {
                                    Log.e("DataDownload", "Failed to create P2P group")
                                    // Fix: wrap withContext in a coroutine scope
                                    CoroutineScope(Dispatchers.Main).launch {
                                        showDownloadError("Failed to create P2P group")
                                    }
                                }
                            }
                        }

                        override fun onWifiP2pDisabled() {
                            Log.e("DataDownload", "WiFi P2P disabled")
                        }

                        override fun onPeersChanged(peers: Collection<WifiP2pDevice>) {
                            Log.i("DataDownload", "Found ${peers.size} P2P devices")
                        }

                        override fun onThisDeviceChanged(device: WifiP2pDevice) {
                            Log.i("DataDownload", "This device changed: ${device.deviceName} - ${device.status}")
                        }

                        override fun onConnected(info: WifiP2pInfo) {
                            Log.i("DataDownload", "P2P connected: groupFormed=${info.groupFormed}, isGroupOwner=${info.isGroupOwner}")
                        }

                        override fun onDisconnected() {
                            Log.i("DataDownload", "P2P disconnected")
                        }

                        override fun onPeerDiscoveryStarted() {
                            Log.i("DataDownload", "Peer discovery started")
                        }

                        override fun onPeerDiscoveryFailed(reason: Int) {
                            Log.e("DataDownload", "Peer discovery failed: $reason")
                        }

                        override fun onConnectRequestSent() {
                            Log.i("DataDownload", "Connect request sent")
                        }

                        override fun onConnectRequestFailed(reason: Int) {
                            Log.e("DataDownload", "Connect request failed: $reason")
                        }

                        override fun connecting() {
                            Log.i("DataDownload", "Connecting to P2P device...")
                        }

                        override fun cancelConnect() {
                            Log.i("DataDownload", "P2P connection cancelled")
                        }

                        override fun cancelConnectFail(reason: Int) {
                            Log.e("DataDownload", "Cancel connect failed: $reason")
                        }

                        override fun retryAlsoFailed() {
                            Log.e("DataDownload", "P2P connection retry failed")
                        }
                    })

                } finally {
                    wifiP2pManager.removeGroup { success ->
                        Log.i("DataDownload", "P2P group removed: $success")
                    }
                    wifiP2pManager.unregisterReceiver(receiver)
                }

            } catch (e: Exception) {
                Log.e("DataDownload", "Error during data download: ${e.message}", e)
            }
        }
    }

    private fun getDeviceIpFromBLE(): String? {
        return "192.168.49.79"
    }

    private fun downloadMediaList(deviceIp: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "http://$deviceIp/files/media.config"
                Log.i("DataDownload", "Downloading media list from: $url")

                val connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 30000

                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val inputStream = connection.inputStream
                    val content = inputStream.bufferedReader().use { it.readText() }

                    Log.i("DataDownload", "=== MEDIA CONFIG CONTENT ===")
                    Log.i("DataDownload", content)
                    Log.i("DataDownload", "=== END MEDIA CONFIG ===")

                    parseMediaList(content)

                    withContext(Dispatchers.Main) {
                        showDownloadSuccess("Media list downloaded successfully")
                    }
                } else {
                    Log.e("DataDownload", "Failed to download media list. Response code: ${connection.responseCode}")
                    withContext(Dispatchers.Main) {
                        showDownloadError("Failed to download media list. Response code: ${connection.responseCode}")
                    }
                }

                connection.disconnect()
            } catch (e: Exception) {
                Log.e("DataDownload", "Error downloading media list: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    when (e) {
                        is java.io.IOException -> {
                            if (e.message?.contains("Cleartext HTTP traffic") == true) {
                                showDownloadError("Network security blocked HTTP connection. Please check app settings.")
                            } else if (e.message?.contains("Failed to connect") == true) {
                                showDownloadError("Cannot connect to glasses device. Please ensure P2P connection is established.")
                            } else {
                                showDownloadError("Network error: ${e.message}")
                            }
                        }
                        else -> showDownloadError("Download failed: ${e.message}")
                    }
                }
            }
        }
    }

    // Fix: marked as suspend so withContext calls inside are valid
    private suspend fun parseMediaList(content: String) {
        Log.i("DataDownload", "Parsing media list content...")

        try {
            val lines = content.trim().split("\n")
            val jpgFiles = mutableListOf<String>()

            lines.forEach { line ->
                val trimmedLine = line.trim()
                if (trimmedLine.isNotEmpty()) {
                    if (trimmedLine.endsWith(".jpg", ignoreCase = true) ||
                        trimmedLine.endsWith(".jpeg", ignoreCase = true)) {
                        jpgFiles.add(trimmedLine)
                        Log.i("DataDownload", "Found JPG file: $trimmedLine")
                    } else {
                        Log.i("DataDownload", "Found non-JPG file: $trimmedLine")
                    }
                }
            }

            Log.i("DataDownload", "Total JPG files found: ${jpgFiles.size}")

            if (jpgFiles.isNotEmpty()) {
                downloadAllJpgFiles(jpgFiles)
            } else {
                Log.w("DataDownload", "No JPG files found in media.config")
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

    private fun downloadAllJpgFiles(jpgFiles: List<String>) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.i("DataDownload", "Starting download of ${jpgFiles.size} JPG files...")

            var successCount = 0
            var failCount = 0

            for ((index, fileName) in jpgFiles.withIndex()) {
                try {
                    Log.i("DataDownload", "Downloading file ${index + 1}/${jpgFiles.size}: $fileName")

                    val success = downloadSingleJpgFile(fileName)
                    if (success) {
                        successCount++
                        Log.i("DataDownload", "\u2713 Successfully downloaded: $fileName")
                    } else {
                        failCount++
                        Log.e("DataDownload", "\u2717 Failed to download: $fileName")
                    }

                    delay(500)

                } catch (e: Exception) {
                    failCount++
                    Log.e("DataDownload", "Error downloading $fileName: ${e.message}", e)
                }
            }

            val message = "Download completed: $successCount successful, $failCount failed"
            Log.i("DataDownload", message)

            withContext(Dispatchers.Main) {
                if (failCount == 0) {
                    showDownloadSuccess("All $successCount files downloaded successfully!")
                } else {
                    showDownloadError("Download completed with errors: $successCount successful, $failCount failed")
                }
            }
        }
    }

    private suspend fun downloadSingleJpgFile(fileName: String): Boolean {
        return try {
            val deviceIp = getDeviceIpFromBLE() ?: return false
            val url = "http://$deviceIp/files/$fileName"
            Log.i("DataDownload", "Downloading: $url")

            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 30000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val file = File(getExternalFilesDir("DCIM"), fileName)
                val outputStream = FileOutputStream(file)

                val buffer = ByteArray(8192)
                var bytesRead: Int
                var totalBytes = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytes += bytesRead
                }

                outputStream.close()
                inputStream.close()

                Log.i("DataDownload", "File downloaded: $fileName ($totalBytes bytes)")

                saveToAlbum(file, fileName)

                true
            } else {
                Log.e("DataDownload", "Failed to download $fileName. Response code: ${connection.responseCode}")
                false
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

    private fun testConnection(deviceIp: String): Boolean {
        Log.i("DataDownload", "Testing connection to $deviceIp...")
        try {
            val url = URL("http://$deviceIp/files/media.config")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            Log.i("DataDownload", "Connection test response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val inputStream = connection.inputStream
                val buffer = ByteArray(1024)
                val bytesRead = inputStream.read(buffer)
                inputStream.close()

                Log.i("DataDownload", "Connection test successful - read $bytesRead bytes")
                return true
            }

            return false
        } catch (e: Exception) {
            Log.e("DataDownload", "Connection test failed: ${e.message}", e)
            return false
        }
    }

    inner class MyDeviceNotifyListener : GlassesDeviceNotifyListener() {

        @RequiresApi(Build.VERSION_CODES.O)
        override fun parseData(cmdType: Int, response: GlassesDeviceNotifyRsp) {
            when (response.loadData[6].toInt()) {
                0x05 -> {
                    val battery = response.loadData[7].toInt()
                    val changing = response.loadData[8].toInt()
                }
                0x02 -> {
                    if (response.loadData.size > 9 && response.loadData[9].toInt() == 0x02) { }
                    LargeDataHandler.getInstance().getPictureThumbnails { cmdType, success, data -> }
                }
                0x03 -> {
                    if (response.loadData[7].toInt() == 1) { }
                }
                0x04 -> {
                    try {
                        val download = response.loadData[7].toInt()
                        val soc = response.loadData[8].toInt()
                        val nor = response.loadData[9].toInt()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                0x0c -> {
                    if (response.loadData[7].toInt() == 1) { }
                }
                0x0d -> {
                    if (response.loadData[7].toInt() == 1) { }
                }
                0x0e -> { }
                0x10 -> { }
                0x12 -> {
                    response.loadData[8].toInt()
                    response.loadData[9].toInt()
                    response.loadData[10].toInt()
                    response.loadData[12].toInt()
                    response.loadData[13].toInt()
                    response.loadData[14].toInt()
                    response.loadData[16].toInt()
                    response.loadData[17].toInt()
                    response.loadData[18].toInt()
                    response.loadData[19].toInt()
                }
            }
        }
    }
}

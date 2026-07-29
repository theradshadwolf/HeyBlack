package com.sdk.glassessdksample.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import java.io.File

class PictureVm(
    private val p2p: P2PController,
    private val ble: BleIpBridge,
    private val dl: AlbumDownloader
) : ViewModel() {

    sealed class DownloadEvent {
        data class FileDownloaded(val file: File, val remaining: Int) : DownloadEvent()
        object AllFilesDownloaded : DownloadEvent()
        data class Error(val message: String) : DownloadEvent()
        data class IpReceived(val ip: String) : DownloadEvent()
        data class P2PGroupCreated(val success: Boolean) : DownloadEvent()
    }

    private val _downloadEvents = MutableSharedFlow<DownloadEvent>()
    val downloadEvents = _downloadEvents.asSharedFlow()

    fun startPhotoPullProcess() {
        viewModelScope.launch {
            Log.i("VM", "Attempting to create P2P group...")
            p2p.createGroup { ok ->
                Log.i("VM", "createGroup result: $ok")
                viewModelScope.launch { _downloadEvents.emit(DownloadEvent.P2PGroupCreated(ok)) }
                if (!ok) {
                    viewModelScope.launch { _downloadEvents.emit(DownloadEvent.Error("Failed to create P2P group")) }
                }
            }
        }

        viewModelScope.launch {
            ble.ip.filterNotNull().take(1).collect { ip ->
                Log.i("VM", "Device IP received via BLE: $ip")
                _downloadEvents.emit(DownloadEvent.IpReceived(ip))
                try {
                    val items = dl.fetchConfig(ip)
                    Log.i("VM", "Total files from config: ${items.size}")
                    if (items.isEmpty()) {
                        _downloadEvents.emit(DownloadEvent.AllFilesDownloaded)
                        return@collect
                    }

                    var left = items.size
                    for (it in items) {
                        val f = dl.fetchOne(ip, it.fileName)
                        if (f != null) {
                            Log.i("VM", "Saved -> ${f.absolutePath}")
                            left--
                            _downloadEvents.emit(DownloadEvent.FileDownloaded(f, left))
                        } else {
                            Log.e("VM", "Failed to download ${it.fileName}")
                            _downloadEvents.emit(DownloadEvent.Error("Failed to download ${it.fileName}"))
                        }
                    }
                    _downloadEvents.emit(DownloadEvent.AllFilesDownloaded)
                } catch (e: Exception) {
                    Log.e("VM", "Error during download process for IP $ip", e)
                    _downloadEvents.emit(DownloadEvent.Error("Error during download: ${e.message}"))
                }
            }
        }
    }

    fun cleanupP2P() {
        Log.i("VM", "Cleaning up P2P connection.")
        p2p.removeGroup { removed ->
            Log.i("VM", "P2P group removal status: $removed")
        }
    }

    override fun onCleared() {
        super.onCleared()
        Log.d("VM", "PictureVm onCleared")
    }
}
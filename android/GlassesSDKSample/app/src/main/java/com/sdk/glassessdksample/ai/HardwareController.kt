package com.sdk.glassessdksample.ai

import android.content.Context
import android.hardware.camera2.CameraManager
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import java.io.File
import java.util.Locale

class HardwareController(private val context: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var currentAudioFile: File? = null

    // 1. Torch / Flashlight Control
    fun toggleFlashlight(state: Boolean): String {
        return try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, state)
            if (state) "Flashlight turned ON." else "Flashlight turned OFF."
        } catch (e: Exception) {
            "Failed to toggle flashlight: ${e.message}"
        }
    }

    // 2. High-Quality Voice / Audio Recording
    fun startAudioRecording(): String {
        if (isRecording) return "Audio recording is already in progress."
        return try {
            val outputFile = File(context.externalCacheDir, "glasses_recording_${System.currentTimeMillis()}.mp3")
            currentAudioFile = outputFile
            
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            "Started audio recording to ${outputFile.name}"
        } catch (e: Exception) {
            "Failed to start recording: ${e.message}"
        }
    }

    fun stopAudioRecording(): String {
        if (!isRecording) return "No audio recording is currently active."
        return try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false
            val filePath = currentAudioFile?.absolutePath ?: "unknown"
            "Stopped audio recording. File saved at: $filePath"
        } catch (e: Exception) {
            isRecording = false
            "Error stopping recording: ${e.message}"
        }
    }

    // 3. Spoken Audio Feedback to Glasses Speakers
    fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "GlassesTTS")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        if (isRecording) stopAudioRecording()
    }
}

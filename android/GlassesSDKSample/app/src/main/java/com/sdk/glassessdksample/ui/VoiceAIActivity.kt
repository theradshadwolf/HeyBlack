package com.sdk.glassessdksample.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sdk.glassessdksample.R
import com.sdk.glassessdksample.ai.GeminiGlassesService
import com.sdk.glassessdksample.ai.HardwareController

class VoiceAIActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VoiceAIActivity"
        private const val RECORD_AUDIO_REQUEST = 1001
    }

    private lateinit var btnListen: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvConversation: TextView
    private lateinit var scrollView: ScrollView

    private var speechRecognizer: SpeechRecognizer? = null
    private lateinit var hardwareController: HardwareController
    private lateinit var geminiService: GeminiGlassesService

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_ai)

        btnListen = findViewById(R.id.btn_listen)
        tvStatus = findViewById(R.id.tv_status)
        tvConversation = findViewById(R.id.tv_conversation)
        scrollView = findViewById(R.id.scroll_conversation)

        hardwareController = HardwareController(this)

        // Retrieve API key from intent extras; fall back to empty (user must configure)
        val apiKey = intent.getStringExtra("GEMINI_API_KEY") ?: ""
        geminiService = GeminiGlassesService(this, apiKey, hardwareController)

        btnListen.setOnClickListener { checkPermissionAndListen() }

        // Back button in title bar area
        val btnBack = findViewById<View>(R.id.btn_back_voice_ai)
        btnBack?.setOnClickListener { finish() }
    }

    private fun checkPermissionAndListen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_REQUEST
            )
        } else {
            startListening()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startListening()
        } else {
            tvStatus.text = getString(R.string.voice_ai_permission_denied)
        }
    }

    private fun startListening() {
        if (speechRecognizer == null) {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                tvStatus.text = getString(R.string.voice_ai_not_available)
                return
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(RecognitionHandler())
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        btnListen.isEnabled = false
        tvStatus.text = getString(R.string.voice_ai_listening)
        speechRecognizer?.startListening(intent)
    }

    private fun appendConversation(speaker: String, text: String) {
        mainHandler.post {
            val current = tvConversation.text.toString()
            val line = if (current.isEmpty()) "$speaker: $text" else "\n\n$speaker: $text"
            tvConversation.append(line)
            scrollView.post { scrollView.fullScroll(View.FOCUS_DOWN) }
        }
    }

    private fun sendToAI(userText: String) {
        appendConversation(getString(R.string.voice_ai_you), userText)
        mainHandler.post { tvStatus.text = getString(R.string.voice_ai_thinking) }

        geminiService.processVoiceQuery(userText) { reply ->
            appendConversation(getString(R.string.voice_ai_ai), reply)
            mainHandler.post {
                tvStatus.text = getString(R.string.voice_ai_ready)
                btnListen.isEnabled = true
            }
        }
    }

    inner class RecognitionHandler : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            mainHandler.post { tvStatus.text = getString(R.string.voice_ai_listening) }
        }

        override fun onBeginningOfSpeech() {}

        override fun onRmsChanged(rmsdB: Float) {}

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            mainHandler.post { tvStatus.text = getString(R.string.voice_ai_processing) }
        }

        override fun onError(error: Int) {
            val msg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> getString(R.string.voice_ai_error_audio)
                SpeechRecognizer.ERROR_NO_MATCH -> getString(R.string.voice_ai_error_no_match)
                SpeechRecognizer.ERROR_NETWORK -> getString(R.string.voice_ai_error_network)
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> getString(R.string.voice_ai_error_timeout)
                else -> getString(R.string.voice_ai_error_generic, error)
            }
            Log.e(TAG, "Speech recognition error: $msg")
            mainHandler.post {
                tvStatus.text = msg
                btnListen.isEnabled = true
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = matches?.firstOrNull()
            if (!text.isNullOrBlank()) {
                sendToAI(text)
            } else {
                mainHandler.post {
                    tvStatus.text = getString(R.string.voice_ai_ready)
                    btnListen.isEnabled = true
                }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                mainHandler.post { tvStatus.text = partial }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
        speechRecognizer = null
        hardwareController.destroy()
    }
}

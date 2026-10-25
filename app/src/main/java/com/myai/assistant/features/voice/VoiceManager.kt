// File: app/src/main/java/com/myai/assistant/features/voice/VoiceManager.kt
// Voice Manager — Speech-to-Text (STT) only
// TTS is handled by GeminiTTSManager

package com.myai.assistant.features.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Voice Manager — Speech-to-Text (STT) Engine
 *
 * Flow:
 * 1. User taps mic or wake word triggers → startListening()
 * 2. Requests transient audio focus (pauses/ducks background music)
 * 3. Real-time partial results → onPartialResult
 * 4. Final result → onResult
 * 5. On completion/error/timeout → cleanly abandons audio focus and resets state
 */
@Singleton
class VoiceManager @Inject constructor() {

    companion object {
        private const val TAG = "VoiceManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var context: Context? = null
    private var audioManager: AudioManager? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Audio Focus tracking
    private var audioFocusRequest: AudioFocusRequest? = null
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        Log.d(TAG, "Audio focus changed: $focusChange")
        if (focusChange == AudioManager.AUDIOFOCUS_LOSS) {
            cancelListening()
        }
    }

    // States
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    // Callbacks
    var onResult: ((String) -> Unit)? = null
    var onPartialResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null
    var onTimeout: (() -> Unit)? = null

    // ═══════════════════════════════════════════════════════
    // INITIALIZE
    // ═══════════════════════════════════════════════════════

    fun initialize(context: Context) {
        val appContext = context.applicationContext
        this.context = appContext
        this.audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        mainHandler.post {
            recreateSpeechRecognizer()
        }
    }

    private fun recreateSpeechRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning old speech recognizer: ${e.message}")
        }
        speechRecognizer = null

        val ctx = context
        if (ctx != null && SpeechRecognizer.isRecognitionAvailable(ctx)) {
            try {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                    setupRecognitionListener()
                }
                Log.d(TAG, "✅ STT initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create SpeechRecognizer: ${e.message}", e)
            }
        } else {
            Log.e(TAG, "❌ Speech recognition not available on this device")
        }
    }

    // ═══════════════════════════════════════════════════════
    // AUDIO FOCUS MANAGEMENT
    // ═══════════════════════════════════════════════════════

    private fun requestAudioFocus(): Boolean {
        val am = audioManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANT)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusChangeListener, mainHandler)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audioFocusRequest = request
                am.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to request audio focus: ${e.message}")
            false
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let {
                    am.abandonAudioFocusRequest(it)
                    audioFocusRequest = null
                }
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusChangeListener)
            }
            Log.d(TAG, "Audio focus abandoned")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to abandon audio focus: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════
    // SPEECH-TO-TEXT (Listening)
    // ═══════════════════════════════════════════════════════

    fun startListening() {
        mainHandler.post {
            val ctx = context
            if (ctx == null) {
                Log.e(TAG, "Context is null, cannot start listening")
                _isListening.value = false
                abandonAudioFocus()
                onError?.invoke("Assistant ready nahi hai")
                return@post
            }

            // If already listening, cancel previous turn cleanly
            if (_isListening.value) {
                try {
                    speechRecognizer?.cancel()
                } catch (_: Exception) {}
            }

            // Acquire clean audio focus before opening microphone
            requestAudioFocus()

            if (speechRecognizer == null) {
                recreateSpeechRecognizer()
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)

                // Minimize silence detection latency for natural conversational feel
                putExtra("android.speech.extras.SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS", 1200L)
                putExtra("android.speech.extras.SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS", 1000L)
                putExtra("android.speech.extras.SPEECH_INPUT_MINIMUM_LENGTH_MILLIS", 1500L)
            }

            try {
                speechRecognizer?.startListening(intent)
                _isListening.value = true
                Log.d(TAG, "🎤 Listening started")
            } catch (e: Exception) {
                Log.w(TAG, "Listen error, recreating recognizer: ${e.message}")
                try {
                    recreateSpeechRecognizer()
                    speechRecognizer?.startListening(intent)
                    _isListening.value = true
                    Log.d(TAG, "🎤 Listening started after recreation")
                } catch (ex: Exception) {
                    Log.e(TAG, "Listen retry failed: ${ex.message}")
                    _isListening.value = false
                    abandonAudioFocus()
                    onError?.invoke("Microphone start nahi ho paya: ${ex.message}")
                }
            }
        }
    }

    fun stopListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                Log.w(TAG, "Stop listening error: ${e.message}")
            }
            _isListening.value = false
            abandonAudioFocus()
        }
    }

    fun cancelListening() {
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.w(TAG, "Cancel listening error: ${e.message}")
            }
            _isListening.value = false
            abandonAudioFocus()
            Log.d(TAG, "🎤 Listening cancelled")
        }
    }

    private fun setupRecognitionListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "🎤 Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "🎤 Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) { /* Volume level */ }
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "🎤 Speech ended, processing...")
            }

            override fun onError(error: Int) {
                _isListening.value = false
                abandonAudioFocus()

                try {
                    speechRecognizer?.cancel()
                } catch (_: Exception) {}

                when (error) {
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                    SpeechRecognizer.ERROR_NO_MATCH -> {
                        Log.d(TAG, "Speech timeout or no match (code $error). Handling gracefully.")
                        onTimeout?.invoke()
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        Log.w(TAG, "Recognizer busy ($error), recreating recognizer instance")
                        recreateSpeechRecognizer()
                        onTimeout?.invoke()
                    }
                    SpeechRecognizer.ERROR_AUDIO -> {
                        Log.e(TAG, "Microphone / Audio recording error ($error)")
                        recreateSpeechRecognizer()
                        onError?.invoke("Microphone problem ($error)")
                    }
                    SpeechRecognizer.ERROR_CLIENT -> {
                        Log.w(TAG, "Client-side recognition cancellation/error ($error)")
                        onTimeout?.invoke()
                    }
                    SpeechRecognizer.ERROR_NETWORK,
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> {
                        Log.w(TAG, "Network connection issue for speech recognition ($error)")
                        onError?.invoke("Network connection issue ($error)")
                    }
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        Log.e(TAG, "Microphone permission required ($error)")
                        onError?.invoke("Microphone permission chahiye")
                    }
                    SpeechRecognizer.ERROR_SERVER,
                    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> {
                        Log.e(TAG, "Speech recognition server error ($error)")
                        onError?.invoke("Voice server error ($error)")
                    }
                    else -> {
                        val msg = "Voice error ($error)"
                        Log.w(TAG, msg)
                        onError?.invoke(msg)
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                abandonAudioFocus()

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotBlank()) {
                    Log.d(TAG, "🎤 Final Result: $text")
                    onResult?.invoke(text)
                } else {
                    Log.d(TAG, "🎤 Empty result received")
                    onTimeout?.invoke()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()?.trim() ?: ""
                if (text.isNotBlank()) {
                    onPartialResult?.invoke(text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
    }

    // ═══════════════════════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════════════════════

    fun destroy() {
        mainHandler.post {
            _isListening.value = false
            abandonAudioFocus()
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying speech recognizer: ${e.message}")
            }
            speechRecognizer = null
            context = null
            audioManager = null
            onResult = null
            onPartialResult = null
            onError = null
            onTimeout = null
            Log.d(TAG, "Voice Manager destroyed cleanly")
        }
    }
}

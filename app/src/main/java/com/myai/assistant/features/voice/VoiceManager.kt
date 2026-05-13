// File: app/src/main/java/com/myai/assistant/features/voice/VoiceManager.kt
// Voice Manager — Speech-to-Text (STT) only
// TTS is now handled by GeminiTTSManager

package com.myai.assistant.features.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
 * Voice Manager — Speech-to-Text (Sunna)
 *
 * Flow:
 * 1. User mic button dabaye → startListening()
 * 2. Real-time partial results → UI mein show
 * 3. Final result → AssistantViewModel ko do → AI brain ko bhejo
 *
 * TTS (bolna) ab GeminiTTSManager handle karta hai
 */
@Singleton
class VoiceManager @Inject constructor() {

    companion object {
        private const val TAG = "VoiceManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private var context: Context? = null

    // States
    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    // Callbacks
    var onResult: ((String) -> Unit)? = null
    var onPartialResult: ((String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // ═══════════════════════════════════════════════════════
    // INITIALIZE
    // ═══════════════════════════════════════════════════════

    fun initialize(context: Context) {
        this.context = context

        // STT check & initialize
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            setupRecognitionListener()
            Log.d(TAG, "✅ STT initialized")
        } else {
            Log.e(TAG, "❌ Speech recognition not available")
        }
    }

    // ═══════════════════════════════════════════════════════
    // SPEECH-TO-TEXT (Sunna)
    // ═══════════════════════════════════════════════════════

    fun startListening() {
        if (_isListening.value) return

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")  // Hindi
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        try {
            speechRecognizer?.startListening(intent)
            _isListening.value = true
            Log.d(TAG, "🎤 Listening started")
        } catch (e: Exception) {
            Log.e(TAG, "Listen error: ${e.message}")
            onError?.invoke("Sunne mein dikkat: ${e.message}")
        }
    }

    fun stopListening() {
        speechRecognizer?.stopListening()
        _isListening.value = false
    }

    private fun setupRecognitionListener() {
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "🎤 Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "🎤 Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) { /* volume level */ }
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isListening.value = false
                Log.d(TAG, "🎤 Speech ended")
            }

            override fun onError(error: Int) {
                _isListening.value = false
                val msg = when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH -> "Kuch samajh nahi aaya, dobara bolo"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Kuch nahi suna, time out ho gaya"
                    SpeechRecognizer.ERROR_AUDIO -> "Audio problem hai"
                    SpeechRecognizer.ERROR_NETWORK -> "Network chahiye voice ke liye"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy hai, thodi der mein try karo"
                    else -> "Voice error ($error)"
                }
                Log.w(TAG, "STT Error: $msg")
                onError?.invoke(msg)
            }

            override fun onResults(results: Bundle?) {
                _isListening.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
                if (text.isNotBlank()) {
                    Log.d(TAG, "🎤 Result: $text")
                    onResult?.invoke(text)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull() ?: ""
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
        speechRecognizer?.destroy()
        speechRecognizer = null
        context = null
        Log.d(TAG, "Voice Manager destroyed")
    }
}

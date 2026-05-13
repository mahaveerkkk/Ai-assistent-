// File: app/src/main/java/com/myai/assistant/features/voice/WakeWordDetector.kt
// Wake Word Detector — "Hey Assistant" / "Suno" background detection

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
 * Wake Word Detector — Background mein continuously listen kare
 *
 * Supported wake words:
 * - "Hey Assistant"
 * - "Suno"
 * - "OK Assistant"
 * - "Hello Assistant"
 *
 * Flow:
 * 1. SpeechRecognizer loop mein chale
 * 2. Result mein wakeword check karo
 * 3. Wakeword mila → onWakeWordDetected callback fire karo
 * 4. Caller full listening mode ON kare
 */
@Singleton
class WakeWordDetector @Inject constructor() {

    companion object {
        private const val TAG = "WakeWord"

        // Wake word list — lowercase mein match hoga
        private val WAKE_WORDS = listOf(
            "hey assistant",
            "hello assistant",
            "ok assistant",
            "suno",
            "sun",
            "suno assistant",
            "assistant",
            "hey ai",
            "ok ai"
        )

        // Restart delay after each recognition cycle (ms)
        private const val RESTART_DELAY_MS = 300L
    }

    // ═══════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════

    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _isDetecting = MutableStateFlow(false)
    val isDetecting: StateFlow<Boolean> = _isDetecting.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    private var isLooping = false
    private var context: Context? = null

    // Callbacks
    var onWakeWordDetected: ((detectedWord: String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // ═══════════════════════════════════════
    // START / STOP
    // ═══════════════════════════════════════

    /**
     * Wakeword detection START karo
     * Background mein continuous loop chalega
     */
    fun start(context: Context) {
        if (_isActive.value) {
            Log.d(TAG, "Already active, skipping start")
            return
        }

        this.context = context

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "❌ Speech recognition not available on this device")
            onError?.invoke("Speech recognition available nahi hai")
            return
        }

        _isActive.value = true
        isLooping = true
        Log.d(TAG, "🎤 Wake word detection STARTED")

        startListeningLoop()
    }

    /**
     * Wakeword detection STOP karo
     */
    fun stop() {
        isLooping = false
        _isActive.value = false
        _isDetecting.value = false

        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
            speechRecognizer = null
        } catch (_: Exception) {}

        Log.d(TAG, "🛑 Wake word detection STOPPED")
    }

    /**
     * Temporarily pause karo (jab main listening chal rahi ho)
     */
    fun pause() {
        if (!_isActive.value) return
        isLooping = false
        _isDetecting.value = false
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
        } catch (_: Exception) {}
        Log.d(TAG, "⏸️ Wake word PAUSED")
    }

    /**
     * Resume karo (jab main listening khatam ho)
     */
    fun resume() {
        if (!_isActive.value) return
        isLooping = true
        Log.d(TAG, "▶️ Wake word RESUMED")
        startListeningLoop()
    }

    // ═══════════════════════════════════════
    // RECOGNITION LOOP
    // ═══════════════════════════════════════

    private fun startListeningLoop() {
        val ctx = context ?: return
        if (!isLooping) return

        try {
            // Purana recognizer clean karo
            speechRecognizer?.destroy()

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
            speechRecognizer?.setRecognitionListener(createWakeWordListener())

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(
                    RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false
                )
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Short timeout for wakeword — quick cycle
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500
                )
            }

            speechRecognizer?.startListening(intent)
            _isDetecting.value = true

        } catch (e: Exception) {
            Log.e(TAG, "Recognition loop error: ${e.message}")
            _isDetecting.value = false
            // Retry after delay
            scheduleRestart()
        }
    }

    private fun createWakeWordListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.v(TAG, "🎤 Listening for wake word...")
            }

            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _isDetecting.value = false
            }

            override fun onError(error: Int) {
                _isDetecting.value = false
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        // Normal — kuch nahi bola, restart karo
                        scheduleRestart()
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        // Thodi der mein retry
                        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                            scheduleRestart()
                        }, 1000)
                    }
                    SpeechRecognizer.ERROR_AUDIO -> {
                        Log.w(TAG, "Audio error, restarting...")
                        scheduleRestart()
                    }
                    else -> {
                        Log.w(TAG, "Recognition error: $error")
                        scheduleRestart()
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                _isDetecting.value = false
                val matches = results?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                ) ?: emptyList()

                // Check for wake word in results
                checkForWakeWord(matches)

                // Continue loop
                scheduleRestart()
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                ) ?: emptyList()

                // Partial mein bhi check karo — faster response
                checkForWakeWord(matches)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    // ═══════════════════════════════════════
    // WAKE WORD CHECK
    // ═══════════════════════════════════════

    /**
     * Results mein wake word dhundo
     */
    private fun checkForWakeWord(results: List<String>) {
        for (result in results) {
            val lower = result.lowercase().trim()
            for (wakeWord in WAKE_WORDS) {
                if (lower.contains(wakeWord)) {
                    Log.d(TAG, "🎯 WAKE WORD DETECTED: \"$result\" (matched: \"$wakeWord\")")

                    // Detection ke baad loop pause karo (main listening start hogi)
                    pause()

                    // Callback fire karo
                    onWakeWordDetected?.invoke(result)
                    return
                }
            }
        }
    }

    // ═══════════════════════════════════════
    // RESTART SCHEDULING
    // ═══════════════════════════════════════

    private fun scheduleRestart() {
        if (!isLooping) return

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            if (isLooping) {
                startListeningLoop()
            }
        }, RESTART_DELAY_MS)
    }

    // ═══════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════

    fun destroy() {
        stop()
        context = null
        onWakeWordDetected = null
        onError = null
        Log.d(TAG, "WakeWordDetector destroyed")
    }
}

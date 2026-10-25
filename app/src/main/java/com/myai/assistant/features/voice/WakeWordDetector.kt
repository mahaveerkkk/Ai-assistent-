// File: app/src/main/java/com/myai/assistant/features/voice/WakeWordDetector.kt
// Wake Word Detector — "Hey Assistant" / "Friday" / "Suno" background detection

package com.myai.assistant.features.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
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
 * Wake Word Detector — Continuous low-CPU background listening engine
 *
 * Supported wake words:
 * - "Friday", "Hey Friday", "OK Friday", "Hello Friday"
 * - "Hey Assistant", "Hello Assistant", "OK Assistant", "Assistant"
 * - "Suno", "Suno Assistant", "Hey AI", "OK AI"
 *
 * Key Optimizations:
 * 1. Persistent SpeechRecognizer instance — avoids creating/destroying binder proxies every cycle
 * 2. Tiered backoff cooldown on errors — prevents 100% CPU spinning when microphone is contested
 * 3. Exact word-boundary regex matching — eliminates false positives (e.g. "Sunday" matching "sun")
 * 4. Safe mainHandler lifecycle — pause() and stop() cancel all pending execution immediately
 */
@Singleton
class WakeWordDetector @Inject constructor() {

    companion object {
        private const val TAG = "WakeWord"

        // Default restart delay after clean cycle (ms)
        private const val NORMAL_RESTART_DELAY_MS = 350L

        // Regex patterns with word boundaries to eliminate false positives
        private val WAKE_PATTERNS = listOf(
            Regex("\\bfriday\\b", RegexOption.IGNORE_CASE),
            Regex("\\bhey\\s+friday\\b", RegexOption.IGNORE_CASE),
            Regex("\\bok\\s+friday\\b", RegexOption.IGNORE_CASE),
            Regex("\\bhello\\s+friday\\b", RegexOption.IGNORE_CASE),
            Regex("\\bhey\\s+assistant\\b", RegexOption.IGNORE_CASE),
            Regex("\\bhello\\s+assistant\\b", RegexOption.IGNORE_CASE),
            Regex("\\bok\\s+assistant\\b", RegexOption.IGNORE_CASE),
            Regex("\\bassistant\\b", RegexOption.IGNORE_CASE),
            Regex("\\bsuno\\b", RegexOption.IGNORE_CASE),
            Regex("\\bsuno\\s+assistant\\b", RegexOption.IGNORE_CASE),
            Regex("\\bsun\\s+na\\b", RegexOption.IGNORE_CASE),
            Regex("\\bhey\\s+ai\\b", RegexOption.IGNORE_CASE),
            Regex("\\bok\\s+ai\\b", RegexOption.IGNORE_CASE)
        )
    }

    // ═══════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════

    private val WAKE_TOKEN = Any()
    private val _isActive = MutableStateFlow(false)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    private val _isDetecting = MutableStateFlow(false)
    val isDetecting: StateFlow<Boolean> = _isDetecting.asStateFlow()

    private var speechRecognizer: SpeechRecognizer? = null
    @Volatile
    private var isLooping = false
    private var context: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingRestartRunnable: Runnable? = null

    // Error cooldown & backoff management
    private var consecutiveErrorCount = 0

    // Callbacks
    var onWakeWordDetected: ((detectedWord: String) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    // ═══════════════════════════════════════
    // START / STOP / PAUSE / RESUME
    // ═══════════════════════════════════════

    fun start(context: Context) {
        val appContext = context.applicationContext
        this.context = appContext

        if (_isActive.value) {
            Log.d(TAG, "Already active, skipping start")
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            Log.e(TAG, "❌ Speech recognition not available on this device")
            onError?.invoke("Speech recognition available nahi hai")
            return
        }

        _isActive.value = true
        isLooping = true
        consecutiveErrorCount = 0
        Log.d(TAG, "🎤 Wake word detection STARTED")

        mainHandler.post {
            ensureRecognizerReady()
            startListeningCycle()
        }
    }

    fun stop() {
        isLooping = false
        _isActive.value = false
        _isDetecting.value = false
        cancelPendingRestarts()

        mainHandler.post {
            destroyRecognizer()
            Log.d(TAG, "🛑 Wake word detection STOPPED")
        }
    }

    fun pause() {
        if (!_isActive.value) return
        isLooping = false
        _isDetecting.value = false
        cancelPendingRestarts()

        mainHandler.post {
            try {
                speechRecognizer?.cancel()
            } catch (_: Exception) {}
            Log.d(TAG, "⏸️ Wake word PAUSED")
        }
    }

    fun resume() {
        if (!_isActive.value) return
        cancelPendingRestarts()
        isLooping = true
        consecutiveErrorCount = 0
        Log.d(TAG, "▶️ Wake word RESUMED")

        mainHandler.post {
            ensureRecognizerReady()
            scheduleRestart(NORMAL_RESTART_DELAY_MS)
        }
    }

    // ═══════════════════════════════════════
    // INTERNAL RECOGNIZER LIFECYCLE
    // ═══════════════════════════════════════

    private fun ensureRecognizerReady() {
        val ctx = context ?: return
        if (speechRecognizer != null) return

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(ctx).apply {
                setRecognitionListener(createWakeWordListener())
            }
            Log.d(TAG, "SpeechRecognizer instance created for wake word")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create SpeechRecognizer for wake word: ${e.message}")
            speechRecognizer = null
        }
    }

    private fun destroyRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {}
        speechRecognizer = null
    }

    private fun cancelPendingRestarts() {
        pendingRestartRunnable?.let {
            mainHandler.removeCallbacks(it)
            pendingRestartRunnable = null
        }
        mainHandler.removeCallbacksAndMessages(WAKE_TOKEN)
    }

    // ═══════════════════════════════════════
    // RECOGNITION CYCLE
    // ═══════════════════════════════════════

    private fun startListeningCycle() {
        if (!isLooping || !_isActive.value) return
        val ctx = context ?: return

        ensureRecognizerReady()
        val recognizer = speechRecognizer
        if (recognizer == null) {
            scheduleBackoffRestart()
            return
        }

        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                // Short minimum length for wake word quick cycling
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
            }

            recognizer.startListening(intent)
            _isDetecting.value = true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start listening cycle: ${e.message}, scheduling backoff")
            _isDetecting.value = false
            destroyRecognizer()
            scheduleBackoffRestart()
        }
    }

    private fun createWakeWordListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                consecutiveErrorCount = 0 // Recognizer active and ready
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
                        // Normal silence cycle — restart quickly
                        consecutiveErrorCount = 0
                        scheduleRestart(NORMAL_RESTART_DELAY_MS)
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                        Log.w(TAG, "Recognizer busy ($error), recreating instance")
                        destroyRecognizer()
                        scheduleBackoffRestart()
                    }
                    SpeechRecognizer.ERROR_AUDIO -> {
                        Log.w(TAG, "Mic temporarily contested or audio error ($error), applying cooldown")
                        destroyRecognizer()
                        scheduleBackoffRestart()
                    }
                    SpeechRecognizer.ERROR_CLIENT -> {
                        // Client cancel / pause
                        if (isLooping) {
                            scheduleRestart(NORMAL_RESTART_DELAY_MS)
                        }
                    }
                    else -> {
                        Log.w(TAG, "WakeWord recognition error ($error), applying cooldown")
                        scheduleBackoffRestart()
                    }
                }
            }

            override fun onResults(results: Bundle?) {
                _isDetecting.value = false
                consecutiveErrorCount = 0

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                val detected = checkForWakeWord(matches)

                if (!detected && isLooping) {
                    scheduleRestart(NORMAL_RESTART_DELAY_MS)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: emptyList()
                // Fast-path detection on partial stream for immediate response
                checkForWakeWord(matches)
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    // ═══════════════════════════════════════
    // WAKE WORD MATCHING
    // ═══════════════════════════════════════

    private fun checkForWakeWord(results: List<String>): Boolean {
        if (!isLooping) return false

        for (result in results) {
            val trimmed = result.trim()
            for (pattern in WAKE_PATTERNS) {
                if (pattern.containsMatchIn(trimmed)) {
                    Log.d(TAG, "🎯 WAKE WORD DETECTED: \"$trimmed\" (pattern: ${pattern.pattern})")

                    // Immediately pause detection loop to surrender microphone
                    pause()

                    // Fire callback
                    onWakeWordDetected?.invoke(trimmed)
                    return true
                }
            }
        }
        return false
    }

    // ═══════════════════════════════════════
    // RESTART & BACKOFF SCHEDULING
    // ═══════════════════════════════════════

    private fun scheduleRestart(delayMs: Long) {
        if (!isLooping || !_isActive.value) return

        cancelPendingRestarts()
        val runnable = Runnable {
            pendingRestartRunnable = null
            if (isLooping && _isActive.value) {
                startListeningCycle()
            }
        }
        pendingRestartRunnable = runnable
        mainHandler.postAtTime(runnable, WAKE_TOKEN, SystemClock.uptimeMillis() + delayMs)
    }

    private fun scheduleBackoffRestart() {
        consecutiveErrorCount++
        // Tiered exponential backoff: 1s -> 2s -> 5s max
        val backoffMs = when {
            consecutiveErrorCount <= 1 -> 1000L
            consecutiveErrorCount == 2 -> 2000L
            else -> 5000L
        }
        Log.d(TAG, "Applying error cooldown: ${backoffMs}ms (consecutive errors: $consecutiveErrorCount)")
        scheduleRestart(backoffMs)
    }

    // ═══════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════

    fun destroy() {
        stop()
        context = null
        onWakeWordDetected = null
        onError = null
        Log.d(TAG, "WakeWordDetector destroyed cleanly")
    }
}

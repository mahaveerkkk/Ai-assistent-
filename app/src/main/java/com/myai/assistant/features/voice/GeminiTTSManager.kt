// File: app/src/main/java/com/myai/assistant/features/voice/GeminiTTSManager.kt
// Gemini TTS Manager — Natural voice via Gemini Live API (WebSocket)
// Model: gemini-3.8-live — Real-time native audio output (PCM 24kHz / 16kHz)
// Fallback: Android built-in TextToSpeech

package com.myai.assistant.features.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Base64
import android.util.Log
import com.myai.assistant.BuildConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini TTS Manager — Natural AI Voice via Gemini 3.8 Live API
 *
 * Primary: Gemini Live API WebSocket (gemini-3.8-live)
 *   → Real-time native streaming audio
 *   → PCM 16-bit 24kHz (or 16kHz) → AudioTrack MODE_STREAM playback
 *   → Low-latency, zero-leak buffer management
 *
 * Fallback: Android TextToSpeech (offline / network dropped)
 */
@Singleton
class GeminiTTSManager @Inject constructor() {

    companion object {
        private const val TAG = "GeminiTTS"

        // Gemini Live API WebSocket endpoint
        private const val LIVE_API_MODEL = "gemini-3.8-live"
        private const val WS_BASE_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"

        // Default Audio config (Gemini Live API output)
        private const val DEFAULT_SAMPLE_RATE = 24000       // 24kHz output
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_OUT_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Voice options
        const val VOICE_KORE = "Kore"       // Female voice
        const val VOICE_CHARON = "Charon"    // Male voice
        const val VOICE_PUCK = "Puck"        // Male (soft)
        const val VOICE_AOEDE = "Aoede"      // Female (warm)
        const val VOICE_FENRIR = "Fenrir"    // Male (deep)
    }

    // ═══════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    // Config
    var voiceName: String = VOICE_KORE
    var apiKey: String = BuildConfig.GEMINI_API_KEY

    // WebSocket connection
    @Volatile
    private var webSocket: WebSocket? = null
    private val isSetupDone = AtomicBoolean(false)
    private var pendingText: String? = null

    // Audio Playback
    private val audioTrackLock = Any()
    @Volatile
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var currentSampleRate = DEFAULT_SAMPLE_RATE
    private val totalFramesWritten = AtomicLong(0)

    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())

    // Fallback TTS
    private var fallbackTts: TextToSpeech? = null
    @Volatile
    private var isFallbackReady = false
    private val fallbackInitLock = Any()

    // HTTP client for WebSocket
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // WebSocket stays open
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS) // Keep alive
        .build()

    // ═══════════════════════════════════════
    // INITIALIZE
    // ═══════════════════════════════════════
    fun initialize(context: Context) {
        synchronized(fallbackInitLock) {
            if (fallbackTts != null) return

            // Use applicationContext to avoid leaking Activity or Service
            val appContext = context.applicationContext
            fallbackTts = TextToSpeech(appContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = fallbackTts?.setLanguage(Locale("hi", "IN"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        fallbackTts?.setLanguage(Locale.ENGLISH)
                    }
                    isFallbackReady = true
                    Log.d(TAG, "✅ Fallback TTS ready")

                    fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            _isSpeaking.value = true
                        }

                        override fun onDone(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            _isSpeaking.value = false
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            _isSpeaking.value = false
                            Log.w(TAG, "Fallback TTS error code $errorCode for $utteranceId")
                        }
                    })
                } else {
                    Log.e(TAG, "❌ Fallback TTS initialization failed")
                    isFallbackReady = false
                }
            }
        }
    }

    // ═══════════════════════════════════════
    // SPEAK — Main suspend function
    // ═══════════════════════════════════════

    /**
     * Speaks text naturally. Tries Gemini Live API (native audio PCM streaming) first.
     * If network drops, times out, or quota is exceeded, seamlessly falls back to Android TTS.
     * This suspend function finishes when speech playback completes.
     */
    suspend fun speak(text: String) {
        if (text.isBlank()) return

        // Clean text — remove emojis and excessive whitespace
        val cleanText = text.replace(Regex("[\\p{So}\\p{Cn}]"), "").trim()
        if (cleanText.isBlank()) return

        // Cleanly stop any existing playback before starting new utterance
        stop()

        Log.d(TAG, "🔊 Speaking: ${cleanText.take(80)}...")
        _isSpeaking.value = true

        // Try Gemini Live API first if API key is present
        if (apiKey.isNotBlank()) {
            try {
                val liveSuccess = speakWithLiveApi(cleanText)
                if (liveSuccess) {
                    _isSpeaking.value = false
                    return
                }
            } catch (e: CancellationException) {
                _isSpeaking.value = false
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Gemini Live API failed: ${e.message}")
            }
        }

        // Seamless fallback: Android TTS
        Log.d(TAG, "↩️ Seamless fallback to Android TTS")
        speakWithFallback(cleanText)
    }

    // ═══════════════════════════════════════
    // GEMINI LIVE API — Native Audio Streaming
    // ═══════════════════════════════════════

    private suspend fun speakWithLiveApi(text: String): Boolean = withContext(Dispatchers.IO) {
        val audioChannel = Channel<ByteArray>(Channel.UNLIMITED)
        val completionDeferred = CompletableDeferred<Boolean>()
        pendingText = text
        isSetupDone.set(false)
        totalFramesWritten.set(0)
        currentSampleRate = DEFAULT_SAMPLE_RATE

        // Start dedicated streaming playback coroutine
        val localPlaybackJob = scope.launch(Dispatchers.Default) {
            runAudioPlaybackLoop(audioChannel)
        }
        playbackJob = localPlaybackJob

        val wsUrl = "$WS_BASE_URL?key=$apiKey"
        Log.d(TAG, "📡 Connecting to Gemini Live API ($LIVE_API_MODEL)...")

        val request = Request.Builder()
            .url(wsUrl)
            .build()

        val wsListener = object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "✅ WebSocket connected!")
                sendSetupMessage(ws)
            }

            override fun onMessage(ws: WebSocket, messageText: String) {
                handleServerMessage(ws, messageText, audioChannel, completionDeferred)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "❌ WebSocket failure: ${t.message}")
                audioChannel.close(t)
                if (!completionDeferred.isCompleted) {
                    completionDeferred.complete(false)
                }
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $code $reason")
                ws.close(1000, null)
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $code $reason")
                if (!audioChannel.isClosedForSend) {
                    audioChannel.close()
                }
            }
        }

        webSocket = httpClient.newWebSocket(request, wsListener)

        try {
            // Await WebSocket completion (audio fully received & played) with 30s timeout
            val result = withTimeoutOrNull(30_000L) {
                completionDeferred.await()
            } ?: false

            if (!result) {
                Log.w(TAG, "Gemini Live API timed out or did not produce audio")
            }

            // Ensure playback job has finished draining before returning
            localPlaybackJob.join()
            result
        } catch (e: CancellationException) {
            audioChannel.close()
            localPlaybackJob.cancel()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Live API session error: ${e.message}", e)
            audioChannel.close(e)
            localPlaybackJob.cancel()
            false
        } finally {
            closeWebSocket()
            stopAudioTrack()
        }
    }

    /**
     * Setup message for gemini-3.8-live
     */
    private fun sendSetupMessage(ws: WebSocket) {
        val setupJson = JSONObject().apply {
            put("setup", JSONObject().apply {
                put("model", "models/$LIVE_API_MODEL")

                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply {
                        put("AUDIO")
                    })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", voiceName)
                            })
                        })
                    })
                })

                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put(
                                "text",
                                "You are Friday, a helpful and natural voice assistant. " +
                                        "Speak expressively in Hindi/Hinglish. Read the provided text naturally and verbatim. " +
                                        "Do not add any conversational remarks, commentary, or greetings. Strictly read the given text."
                            )
                        })
                    })
                })
            })
        }

        val sent = ws.send(setupJson.toString())
        Log.d(TAG, "📤 Setup message sent: $sent")
    }

    /**
     * Send client content (text to read) once setupComplete is received
     */
    private fun sendTextContent(ws: WebSocket, text: String) {
        val contentJson = JSONObject().apply {
            put("clientContent", JSONObject().apply {
                put("turns", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", text)
                            })
                        })
                    })
                })
                put("turnComplete", true)
            })
        }

        val sent = ws.send(contentJson.toString())
        Log.d(TAG, "📤 Text content sent: $sent — '${text.take(50)}'")
    }

    /**
     * Handle incoming server messages
     */
    private fun handleServerMessage(
        ws: WebSocket,
        messageText: String,
        audioChannel: Channel<ByteArray>,
        completionDeferred: CompletableDeferred<Boolean>
    ) {
        try {
            val json = JSONObject(messageText)

            // Check for API errors
            if (json.has("error")) {
                val errorObj = json.optJSONObject("error")
                val errorMsg = errorObj?.optString("message") ?: "Gemini API error"
                Log.e(TAG, "❌ Live API error response: $errorMsg")
                audioChannel.close(Exception(errorMsg))
                if (!completionDeferred.isCompleted) {
                    completionDeferred.complete(false)
                }
                return
            }

            // Setup complete → send the text content
            if (json.has("setupComplete")) {
                Log.d(TAG, "✅ Setup complete! Sending text...")
                isSetupDone.set(true)
                pendingText?.let { text ->
                    sendTextContent(ws, text)
                    pendingText = null
                }
                return
            }

            // Server Content (Audio streaming)
            if (json.has("serverContent")) {
                val serverContent = json.getJSONObject("serverContent")

                // Interrupted by server
                if (serverContent.optBoolean("interrupted", false)) {
                    Log.w(TAG, "⚠️ Server interrupted turn")
                    audioChannel.close()
                    stopAudioTrack()
                    if (!completionDeferred.isCompleted) {
                        completionDeferred.complete(false)
                    }
                    return
                }

                // Model turn parts (PCM 16-bit audio chunks)
                if (serverContent.has("modelTurn")) {
                    val modelTurn = serverContent.getJSONObject("modelTurn")
                    if (modelTurn.has("parts")) {
                        val parts = modelTurn.getJSONArray("parts")
                        for (i in 0 until parts.length()) {
                            val part = parts.getJSONObject(i)
                            if (part.has("inlineData")) {
                                val inlineData = part.getJSONObject("inlineData")
                                val mimeType = inlineData.optString("mimeType", "")
                                if (mimeType.contains("16000")) {
                                    currentSampleRate = 16000
                                } else if (mimeType.contains("24000")) {
                                    currentSampleRate = 24000
                                }

                                val base64Audio = inlineData.getString("data")
                                val audioBytes = Base64.decode(base64Audio, Base64.DEFAULT)
                                if (audioBytes.isNotEmpty() && !audioChannel.isClosedForSend) {
                                    audioChannel.trySend(audioBytes)
                                }
                            }
                        }
                    }
                }

                // Turn complete → all audio parts sent
                if (serverContent.optBoolean("turnComplete", false)) {
                    Log.d(TAG, "✅ Turn complete from server. Finishing playback...")
                    if (!audioChannel.isClosedForSend) {
                        audioChannel.close()
                    }
                    if (!completionDeferred.isCompleted) {
                        completionDeferred.complete(true)
                    }
                }
            }

            // GoAway warning
            if (json.has("goAway")) {
                Log.w(TAG, "Server issued goAway")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Server message parse error: ${e.message}", e)
            if (!completionDeferred.isCompleted) {
                completionDeferred.complete(false)
            }
        }
    }

    // ═══════════════════════════════════════
    // AUDIO PLAYBACK — AudioTrack MODE_STREAM
    // ═══════════════════════════════════════

    /**
     * Dedicated streaming playback loop:
     * Writes PCM 16-bit chunks directly to AudioTrack in real-time,
     * avoiding high memory allocation and preventing start/end clipping.
     */
    private suspend fun runAudioPlaybackLoop(channel: Channel<ByteArray>) {
        var track: AudioTrack? = null
        var totalBytes = 0L

        try {
            for (chunk in channel) {
                if (chunk.isEmpty()) continue

                // Initialize AudioTrack on first chunk if not yet created
                if (track == null) {
                    val sampleRate = currentSampleRate
                    val minBufSize = AudioTrack.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT)
                    val bufferSize = (minBufSize * 4).coerceAtLeast(8192)

                    synchronized(audioTrackLock) {
                        track = AudioTrack.Builder()
                            .setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                    .build()
                            )
                            .setAudioFormat(
                                AudioFormat.Builder()
                                    .setSampleRate(sampleRate)
                                    .setChannelMask(CHANNEL_CONFIG)
                                    .setEncoding(AUDIO_FORMAT)
                                    .build()
                            )
                            .setBufferSizeInBytes(bufferSize)
                            .setTransferMode(AudioTrack.MODE_STREAM)
                            .build()

                        audioTrack = track
                    }

                    track?.play()
                    Log.d(TAG, "🔊 AudioTrack initialized at ${sampleRate}Hz (MODE_STREAM)")
                }

                // Stream chunk into AudioTrack
                if (!currentCoroutineContext().isActive) break
                track?.let { t ->
                    if (t.state == AudioTrack.STATE_INITIALIZED && t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                        val written = t.write(chunk, 0, chunk.size, AudioTrack.WRITE_BLOCKING)
                        if (written > 0) {
                            totalBytes += written
                            totalFramesWritten.set(totalBytes / 2) // 16-bit = 2 bytes per sample
                        }
                    }
                }
            }

            // Channel closed normally — wait for all queued audio frames to play through DAC
            track?.let { t ->
                if (t.state == AudioTrack.STATE_INITIALIZED && t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                    val sampleRate = currentSampleRate
                    val totalFrames = totalFramesWritten.get()

                    // Wait until playbackHeadPosition reaches total frames, track stopped, or timeout
                    val maxWaitMs = ((totalFrames * 1000L / sampleRate) + 500L).coerceIn(200L, 8_000L)
                    val startTime = System.currentTimeMillis()

                    while (t.playState == AudioTrack.PLAYSTATE_PLAYING &&
                           t.playbackHeadPosition < totalFrames &&
                           (System.currentTimeMillis() - startTime) < maxWaitMs &&
                           currentCoroutineContext().isActive) {
                        delay(25)
                    }

                    // Brief buffer to guarantee physical speaker output finishes smoothly
                    if (currentCoroutineContext().isActive) {
                        delay(40)
                    }
                }
            }

        } catch (e: CancellationException) {
            // Cancelled externally (e.g. stop() called)
            Log.d(TAG, "Audio playback loop cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "Audio playback loop error: ${e.message}", e)
        } finally {
            synchronized(audioTrackLock) {
                try {
                    track?.let { t ->
                        if (t.state == AudioTrack.STATE_INITIALIZED) {
                            t.pause()
                            t.flush()
                            t.stop()
                        }
                        t.release()
                    }
                } catch (_: Exception) {}
                if (audioTrack == track) {
                    audioTrack = null
                }
            }
            Log.d(TAG, "🔊 AudioTrack playback finished and released ($totalBytes bytes)")
        }
    }

    private fun stopAudioTrack() {
        try {
            audioChannel?.close()
        } catch (_: Exception) {}
        playbackJob?.cancel()
        playbackJob = null

        synchronized(audioTrackLock) {
            try {
                audioTrack?.let {
                    if (it.state == AudioTrack.STATE_INITIALIZED) {
                        it.pause()
                        it.flush()
                        it.stop()
                    }
                    it.release()
                }
            } catch (_: Exception) {}
            audioTrack = null
        }
    }

    // ═══════════════════════════════════════
    // WEBSOCKET MANAGEMENT
    // ═══════════════════════════════════════

    private fun closeWebSocket() {
        try {
            webSocket?.close(1000, "Normal closure")
            webSocket = null
            isSetupDone.set(false)
        } catch (_: Exception) {}
    }

    // ═══════════════════════════════════════
    // FALLBACK TTS — Android built-in (Offline)
    // ═══════════════════════════════════════

    private suspend fun speakWithFallback(text: String) = withContext(Dispatchers.Main) {
        // Wait up to 1500ms if fallback is currently initializing
        var waitMs = 0
        while (!isFallbackReady && waitMs < 1500) {
            delay(100)
            waitMs += 100
        }

        if (!isFallbackReady || fallbackTts == null) {
            Log.e(TAG, "❌ Fallback TTS not ready")
            _isSpeaking.value = false
            return@withContext
        }

        val completionDeferred = CompletableDeferred<Unit>()
        val utteranceId = "utterance_${System.currentTimeMillis()}"

        fallbackTts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(id: String?) {
                if (id == utteranceId) {
                    _isSpeaking.value = false
                    if (!completionDeferred.isCompleted) {
                        completionDeferred.complete(Unit)
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId) {
                    _isSpeaking.value = false
                    if (!completionDeferred.isCompleted) {
                        completionDeferred.complete(Unit)
                    }
                }
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId) {
                    _isSpeaking.value = false
                    Log.w(TAG, "Fallback TTS error ($errorCode)")
                    if (!completionDeferred.isCompleted) {
                        completionDeferred.complete(Unit)
                    }
                }
            }
        })

        val result = fallbackTts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            utteranceId
        )

        if (result == TextToSpeech.SUCCESS) {
            try {
                // Wait until speech finishes or timeout (max 25s)
                withTimeoutOrNull(25_000L) {
                    completionDeferred.await()
                }
            } finally {
                _isSpeaking.value = false
            }
        } else {
            Log.e(TAG, "Fallback TTS speak() returned error code $result")
            _isSpeaking.value = false
        }
    }

    // ═══════════════════════════════════════
    // CONTROLS
    // ═══════════════════════════════════════

    fun stop() {
        // Cancel active playback and release AudioTrack
        stopAudioTrack()
        // Close live WebSocket connection
        closeWebSocket()
        // Stop fallback TTS
        try {
            fallbackTts?.stop()
        } catch (_: Exception) {}
        _isSpeaking.value = false
    }

    fun setSpeechRate(rate: Float) {
        fallbackTts?.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
    }

    fun setPitch(pitch: Float) {
        fallbackTts?.setPitch(pitch.coerceIn(0.5f, 2.0f))
    }

    fun updateApiKey(key: String) {
        apiKey = key
    }

    fun updateVoice(voice: String) {
        voiceName = voice
    }

    // ═══════════════════════════════════════
    // CLEANUP
    // ═══════════════════════════════════════

    fun destroy() {
        stop()
        scope.cancel()
        synchronized(fallbackInitLock) {
            try {
                fallbackTts?.shutdown()
            } catch (_: Exception) {}
            fallbackTts = null
            isFallbackReady = false
        }
        Log.d(TAG, "GeminiTTS destroyed")
    }
}

// File: app/src/main/java/com/myai/assistant/ai/LocalInferenceClient.kt
// LiteRT Local Inference Client — Run Qwen/Gemma fully on-device offline

package com.myai.assistant.ai

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.myai.assistant.ai.models.AiParsedResponse
import com.myai.assistant.ai.models.AiResponseParser
import com.myai.assistant.ai.models.AiSource
import com.myai.assistant.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalInferenceClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository
) : AutoCloseable {

    companion object {
        private const val TAG = "LocalInferenceClient"
    }

    private var engine: Engine? = null
    private var isInitialized = false

    /**
     * 🧠 Initialize the local LiteRT-LM engine
     * Model loading should only happen once as it takes several seconds
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        if (isInitialized && engine != null) return@withContext true

        val modelPath = settingsRepository.liteRtModelPath
        val modelFile = File(modelPath)

        if (!modelFile.exists()) {
            Log.w(TAG, "❌ LiteRT model file not found at: $modelPath")
            return@withContext false
        }

        try {
            Log.d(TAG, "⏳ Initializing LiteRT-LM Engine with model: $modelPath...")
            val config = EngineConfig(modelPath = modelPath)
            val newEngine = Engine(config)
            newEngine.initialize()
            
            engine = newEngine
            isInitialized = true
            Log.d(TAG, "✅ LiteRT-LM Engine initialized successfully!")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ LiteRT-LM initialization failed: ${e.message}", e)
            isInitialized = false
            engine = null
            false
        }
    }

    /**
     * 💬 Execute chat query locally on-device
     */
    suspend fun chat(prompt: String): AiParsedResponse = withContext(Dispatchers.Default) {
        val currentEngine = engine
        // Ensure engine is initialized
        if (!isInitialized || currentEngine == null) {
            val initSuccess = initialize()
            val freshEngine = engine
            if (!initSuccess || freshEngine == null) {
                return@withContext AiResponseParser.errorResponse(
                    "LiteRT-LM not initialized. Please verify model path in Settings.",
                    AiSource.FALLBACK
                )
            }
        }

        try {
            Log.d(TAG, "🧠 Running Local Inference...")
            val activeEngine = engine ?: return@withContext AiResponseParser.errorResponse("LiteRT Engine lost", AiSource.FALLBACK)
            
            // Create a conversation session and send message synchronously
            // Using sendMessage(String) which is the stable synchronous API in 0.8.0
            val conversation = activeEngine.createConversation()
            val responseMessage = conversation.sendMessage(prompt)
            val fullResponse = responseMessage.text.trim()
            
            Log.d(TAG, "✅ Local Inference finished: $fullResponse")
            
            // Parse response into standard structure
            val parsed = AiResponseParser.parse(fullResponse, AiSource.OLLAMA)
            parsed
        } catch (e: Exception) {
            Log.e(TAG, "❌ Local Inference failed: ${e.message}", e)
            AiResponseParser.errorResponse("LiteRT error: ${e.message}", AiSource.FALLBACK)
        }
    }

    /**
     * Release system resources (GPU/CPU buffers)
     */
    override fun close() {
        try {
            val current = engine
            current?.close()
            engine = null
            isInitialized = false
            Log.d(TAG, "🔌 LiteRT-LM Engine closed successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LiteRT-LM: ${e.message}")
        }
    }
}

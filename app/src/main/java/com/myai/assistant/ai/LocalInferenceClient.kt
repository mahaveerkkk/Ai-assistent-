// File: app/src/main/java/com/myai/assistant/ai/LocalInferenceClient.kt
// LiteRT Local Inference Client — Stub (API version pinned to 0.8.0)
// NOTE: LiteRT-LM 0.8.0 conversation API is loaded at runtime via reflection
// to avoid compile-time type mismatches. Feature is disabled by default.

package com.myai.assistant.ai

import android.content.Context
import android.util.Log
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

    private var isInitialized = false

    /**
     * Initialize the LiteRT engine via reflection to avoid compile-time API issues
     */
    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        val modelPath = settingsRepository.liteRtModelPath
        val modelFile = File(modelPath)

        if (!modelFile.exists()) {
            Log.w(TAG, "❌ LiteRT model file not found at: $modelPath")
            return@withContext false
        }

        return@withContext try {
            // Use reflection to avoid compile-time dependency on LiteRT-LM API
            val engineConfigClass = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
            val engineClass = Class.forName("com.google.ai.edge.litertlm.Engine")

            val config = engineConfigClass
                .getDeclaredConstructor(String::class.java)
                .newInstance(modelPath)

            val engine = engineClass
                .getDeclaredConstructor(engineConfigClass)
                .newInstance(config)

            engineClass.getMethod("initialize").invoke(engine)

            // Store engine for later use
            _engine = engine
            isInitialized = true
            Log.d(TAG, "✅ LiteRT-LM Engine initialized via reflection")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ LiteRT-LM init failed: ${e.message}")
            isInitialized = false
            _engine = null
            false
        }
    }

    private var _engine: Any? = null

    /**
     * Run inference using reflection — avoids all LiteRT compile-time API issues
     */
    suspend fun chat(prompt: String): AiParsedResponse = withContext(Dispatchers.IO) {
        if (!isInitialized || _engine == null) {
            val initSuccess = initialize()
            if (!initSuccess || _engine == null) {
                return@withContext AiResponseParser.errorResponse(
                    "LiteRT-LM not initialized. Please verify model path in Settings.",
                    AiSource.FALLBACK
                )
            }
        }

        return@withContext try {
            Log.d(TAG, "🧠 Running LiteRT inference via reflection...")
            val engine = _engine ?: return@withContext AiResponseParser.errorResponse(
                "LiteRT Engine unavailable", AiSource.FALLBACK
            )

            val engineClass = engine.javaClass

            // Create conversation
            val conversation = engineClass.getMethod("createConversation").invoke(engine)
            val convClass = conversation!!.javaClass

            // Try to find sendMessage method and build a Message
            val responseText = runInference(convClass, conversation, prompt)

            Log.d(TAG, "✅ LiteRT response: $responseText")
            AiResponseParser.parse(responseText, AiSource.OLLAMA)
        } catch (e: Exception) {
            Log.e(TAG, "❌ LiteRT inference failed: ${e.message}", e)
            AiResponseParser.errorResponse("LiteRT error: ${e.message}", AiSource.FALLBACK)
        }
    }

    private fun runInference(convClass: Class<*>, conversation: Any, prompt: String): String {
        // Try different known API patterns for LiteRT-LM
        val methods = convClass.methods.map { it.name }
        Log.d(TAG, "Available conversation methods: $methods")

        // Pattern 1: sendMessage(String) -> String
        if (methods.contains("sendMessage")) {
            try {
                val sendMsg = convClass.methods.first { it.name == "sendMessage" }
                val paramTypes = sendMsg.parameterTypes
                Log.d(TAG, "sendMessage params: ${paramTypes.map { it.name }}")

                if (paramTypes.size == 1 && paramTypes[0] == String::class.java) {
                    val result = sendMsg.invoke(conversation, prompt)
                    return result?.toString() ?: ""
                }

                // sendMessage(Message) — try to build a Message
                if (paramTypes.size == 1) {
                    val msgClass = paramTypes[0]
                    val msg = buildMessage(msgClass, prompt) ?: return "LiteRT: Could not build Message"
                    val result = sendMsg.invoke(conversation, msg)
                    // Try to get text from result
                    return extractText(result) ?: result?.toString() ?: ""
                }
            } catch (e: Exception) {
                Log.w(TAG, "sendMessage attempt failed: ${e.message}")
            }
        }

        return "LiteRT inference not available in this build."
    }

    private fun buildMessage(msgClass: Class<*>, text: String): Any? {
        // Try common factory patterns
        return try {
            // Pattern: Message.builder().setContent(text).build()
            val builder = msgClass.methods.firstOrNull { it.name == "builder" || it.name == "newBuilder" }
            if (builder != null) {
                var b = builder.invoke(null)
                val bClass = b!!.javaClass
                // setContent or setText
                bClass.methods.firstOrNull { it.name == "setContent" || it.name == "setText" }
                    ?.invoke(b, text)
                b = bClass.methods.firstOrNull { it.name == "setRole" }?.let { m ->
                    val roleClass = m.parameterTypes[0]
                    val userRole = roleClass.enumConstants?.firstOrNull {
                        it.toString().contains("USER", ignoreCase = true)
                    }
                    if (userRole != null) m.invoke(b, userRole) else b
                } ?: b
                bClass.getMethod("build").invoke(b)
            } else {
                // Try direct constructor with String
                msgClass.getDeclaredConstructor(String::class.java).newInstance(text)
            }
        } catch (e: Exception) {
            Log.w(TAG, "buildMessage failed: ${e.message}")
            null
        }
    }

    private fun extractText(obj: Any?): String? {
        if (obj == null) return null
        return try {
            // Try .getText(), .text, .getContent(), .content
            val cls = obj.javaClass
            cls.methods.firstOrNull { it.name == "getText" || it.name == "getContent" }
                ?.invoke(obj)?.toString()
                ?: cls.fields.firstOrNull { it.name == "text" || it.name == "content" }
                    ?.get(obj)?.toString()
        } catch (e: Exception) {
            null
        }
    }

    override fun close() {
        try {
            _engine?.javaClass?.getMethod("close")?.invoke(_engine)
            _engine = null
            isInitialized = false
            Log.d(TAG, "🔌 LiteRT-LM Engine closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing LiteRT-LM: ${e.message}")
        }
    }
}

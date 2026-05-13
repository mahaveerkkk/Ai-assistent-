// File: app/src/main/java/com/myai/assistant/ai/OllamaClient.kt
// Ollama Client — Local AI server se baat karo

package com.myai.assistant.ai

import android.util.Log
import com.google.gson.Gson
import com.myai.assistant.ai.models.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OllamaClient @Inject constructor() {

    companion object {
        private const val TAG = "OllamaClient"

        // Emulator ke liye: 10.0.2.2 (host machine ka localhost)
        // Real device ke liye: apne PC ka IP daalo
        const val EMULATOR_URL = "http://10.0.2.2:11434"
        const val DEFAULT_MODEL = "llama3"
    }

    private val gson = Gson()

    // OkHttp client with generous timeouts (AI slow ho sakta hai)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)   // AI response mein time lagta hai
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Current config — runtime pe change ho sakta hai
    var baseUrl: String = EMULATOR_URL
    var model: String = DEFAULT_MODEL

    /**
     * AI se chat karo — main function
     *
     * @param userMessage User ka message
     * @param chatHistory Previous messages [(role, content)]
     * @return AiParsedResponse
     */
    suspend fun chat(
        userMessage: String,
        chatHistory: List<Pair<String, String>> = emptyList()
    ): AiParsedResponse = withContext(Dispatchers.IO) {
        try {
            // Auto-detect model if default isn't available
            val availableModels = getModels()
            val activeModel = if (availableModels.isNotEmpty() && model !in availableModels) {
                val detected = availableModels.first()
                Log.w(TAG, "Model '$model' not found. Using detected model: $detected")
                detected
            } else {
                model
            }

            // Messages build karo (system prompt + history + current)
            val messages = ChatHistoryBuilder.buildOllamaMessages(
                chatHistory = chatHistory,
                userMessage = userMessage
            )

            // Request body banao
            val request = OllamaRequest(
                model = activeModel,
                messages = messages,
                stream = false,
                format = "json"
            )

            val jsonBody = gson.toJson(request)
            Log.d(TAG, "Request to $baseUrl model=$activeModel")

            // HTTP POST call
            val httpRequest = Request.Builder()
                .url("$baseUrl/api/chat")
                .post(jsonBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(httpRequest).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "Unknown error"
                Log.e(TAG, "HTTP Error ${response.code}: $errorBody")
                return@withContext AiResponseParser.errorResponse(
                    "Ollama error (${response.code}): $errorBody",
                    AiSource.OLLAMA
                )
            }

            // Response parse karo
            val responseBody = response.body?.string() ?: ""
            Log.d(TAG, "Response: ${responseBody.take(200)}")

            val ollamaResponse = gson.fromJson(responseBody, OllamaResponse::class.java)

            if (ollamaResponse.error != null) {
                return@withContext AiResponseParser.errorResponse(
                    ollamaResponse.error,
                    AiSource.OLLAMA
                )
            }

            val aiContent = ollamaResponse.message?.content ?: ""

            // AI response ko parse karo (JSON extract)
            AiResponseParser.parse(aiContent, AiSource.OLLAMA)

        } catch (e: IOException) {
            Log.e(TAG, "Network error: ${e.message}", e)
            AiResponseParser.errorResponse(
                "Ollama se connect nahi ho pa raha. Kya server chal raha hai?",
                AiSource.OLLAMA
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error: ${e.message}", e)
            AiResponseParser.errorResponse(
                "Kuch gadbad ho gayi: ${e.message}",
                AiSource.OLLAMA
            )
        }
    }

    /**
     * Check karo Ollama server reachable hai ya nahi
     */
    suspend fun isServerAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            response.isSuccessful
        } catch (e: Exception) {
            Log.w(TAG, "Server not available: ${e.message}")
            false
        }
    }

    /**
     * Available models ki list lo
     */
    suspend fun getModels(): List<String> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$baseUrl/api/tags")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val map = gson.fromJson(body, Map::class.java)
            val models = map["models"] as? List<Map<String, Any>> ?: return@withContext emptyList()

            models.mapNotNull { it["name"] as? String }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

// File: app/src/main/java/com/myai/assistant/ai/GroqClient.kt
// Groq Client — Blazing fast AI inference (Llama 3.3 70B @ 300+ tok/s)

package com.myai.assistant.ai

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.myai.assistant.BuildConfig
import com.myai.assistant.ai.models.AiParsedResponse
import com.myai.assistant.ai.models.AiResponseParser
import com.myai.assistant.ai.models.AiSource
import com.myai.assistant.ai.models.SYSTEM_PROMPT
import com.myai.assistant.data.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroqClient @Inject constructor(
    private val settingsRepository: SettingsRepository
) {

    companion object {
        private const val TAG = "GroqClient"
        private const val GROQ_URL = "https://api.groq.com/openai/v1/chat/completions"
        const val DEFAULT_MODEL = "openai/gpt-oss-120b"
        val FALLBACK_MODELS = listOf(
            "openai/gpt-oss-120b",
            "openai/gpt-oss-20b",
            "qwen/qwen3.8-27b",
            "llama-3.3-70b-versatile"
        )
    }

    private val gson = Gson()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    var apiKey: String = BuildConfig.GROQ_API_KEY
    var model: String = DEFAULT_MODEL

    init {
        val savedKey = settingsRepository.groqApiKey.trim().removeSurrounding("\"").removeSurrounding("'")
        if (savedKey.isNotBlank()) {
            apiKey = savedKey
            Log.d(TAG, "GroqClient initialized with saved key (len: ${apiKey.length})")
        }
    }

    fun isAvailable(): Boolean = apiKey.isNotBlank()

    fun updateApiKey(key: String) {
        apiKey = key.trim().removeSurrounding("\"").removeSurrounding("'")
        Log.d(TAG, "Groq API key updated (length: ${apiKey.length})")
    }

    suspend fun chat(
        userMessage: String,
        chatHistory: List<Pair<String, String>> = emptyList()
    ): AiParsedResponse = withContext(Dispatchers.IO) {
        if (!isAvailable()) {
            Log.w(TAG, "Groq API key is not configured")
            return@withContext AiResponseParser.errorResponse("Groq API key not set", AiSource.GROQ)
        }

        try {
            val messagesArray = JsonArray()

            // 1. System prompt (instructs assistant to output JSON strictly)
            messagesArray.add(JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", SYSTEM_PROMPT.trimIndent())
            })

            // 2. Chat history (sanitize and map roles)
            val recentHistory = chatHistory.takeLast(8)
            for ((role, content) in recentHistory) {
                if (content.isNotBlank()) {
                    val groqRole = if (role == "assistant" || role == "model") "assistant" else "user"
                    messagesArray.add(JsonObject().apply {
                        addProperty("role", groqRole)
                        addProperty("content", content.trim())
                    })
                }
            }

            // 3. Current user message
            messagesArray.add(JsonObject().apply {
                addProperty("role", "user")
                addProperty("content", userMessage.trim())
            })

            // 4. Request payload with dynamic model fallback and User-Agent header
            val modelsToTry = mutableListOf(model.ifBlank { DEFAULT_MODEL })
            for (m in FALLBACK_MODELS) {
                if (!modelsToTry.contains(m)) modelsToTry.add(m)
            }

            var lastResponse: AiParsedResponse? = null
            for (currentModel in modelsToTry) {
                val requestBodyJson = JsonObject().apply {
                    addProperty("model", currentModel)
                    add("messages", messagesArray)
                    addProperty("temperature", 0.2) // Low temperature for consistent JSON structured output
                    addProperty("max_tokens", 1024)
                    add("response_format", JsonObject().apply {
                        addProperty("type", "json_object")
                    })
                }

                val request = Request.Builder()
                    .url(GROQ_URL)
                    .addHeader("Authorization", "Bearer $apiKey")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("User-Agent", "MyAI-Assistant-Android/1.0")
                    .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                Log.d(TAG, "⚡ Sending request to Groq ($currentModel)...")
                val startTime = System.currentTimeMillis()

                var tryNextModel = false
                httpClient.newCall(request).execute().use { response ->
                    val durationMs = System.currentTimeMillis() - startTime
                    val responseBodyStr = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        val parsedError = parseGroqError(responseBodyStr, response.code)
                        Log.e(TAG, "Groq HTTP ${response.code} ($durationMs ms) on $currentModel: $parsedError")

                        if (response.code == 404 && parsedError.contains("model", ignoreCase = true)) {
                            Log.w(TAG, "Model $currentModel not found on Groq, trying next fallback...")
                            lastResponse = AiResponseParser.errorResponse("Groq model error: $parsedError", AiSource.GROQ)
                            tryNextModel = true
                            return@use
                        }

                        return@withContext when (response.code) {
                            429 -> AiResponseParser.errorResponse(
                                "Groq rate limit exceeded (429). Please wait a moment or check quota.",
                                AiSource.GROQ
                            )
                            401, 403 -> AiResponseParser.errorResponse(
                                "Groq authentication failed (${response.code}): Invalid API Key.",
                                AiSource.GROQ
                            )
                            else -> AiResponseParser.errorResponse(
                                "Groq error ${response.code}: $parsedError",
                                AiSource.GROQ
                            )
                        }
                    }

                    if (responseBodyStr.isBlank()) {
                        Log.e(TAG, "Groq returned empty response body ($durationMs ms)")
                        return@withContext AiResponseParser.errorResponse("Empty response from Groq", AiSource.GROQ)
                    }

                    val jsonObject = JsonParser.parseString(responseBodyStr).asJsonObject
                    val choices = jsonObject.getAsJsonArray("choices")
                    if (choices == null || choices.size() == 0) {
                        Log.e(TAG, "Groq choices array is empty ($durationMs ms)")
                        return@withContext AiResponseParser.errorResponse("No completion choices from Groq", AiSource.GROQ)
                    }

                    val messageObj = choices[0].asJsonObject.getAsJsonObject("message")
                    val replyText = messageObj?.get("content")?.asString.orEmpty()

                    if (replyText.isBlank()) {
                        Log.w(TAG, "Groq message content is blank ($durationMs ms)")
                        return@withContext AiResponseParser.errorResponse("Blank response from Groq", AiSource.GROQ)
                    }

                    Log.d(TAG, "⚡ Groq response received via $currentModel in ${durationMs}ms (${replyText.length} chars)")
                    model = currentModel
                    return@withContext AiResponseParser.parse(replyText, AiSource.GROQ)
                }

                if (!tryNextModel) break
            }

            return@withContext lastResponse ?: AiResponseParser.errorResponse("All Groq models failed", AiSource.GROQ)

        } catch (e: SocketTimeoutException) {
            Log.e(TAG, "Groq timeout: ${e.message}")
            AiResponseParser.errorResponse("Groq request timed out. Network connection may be slow.", AiSource.GROQ)
        } catch (e: UnknownHostException) {
            Log.e(TAG, "Groq unknown host: ${e.message}")
            AiResponseParser.errorResponse("Cannot reach Groq API. Please check your internet connection.", AiSource.GROQ)
        } catch (e: IOException) {
            Log.e(TAG, "Groq network error: ${e.message}")
            AiResponseParser.errorResponse("Groq network error: ${e.message}", AiSource.GROQ)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Groq exception: ${e.message}", e)
            AiResponseParser.errorResponse("Groq error: ${e.message}", AiSource.GROQ)
        }
    }

    private fun parseGroqError(errorBody: String, statusCode: Int): String {
        return try {
            val json = JsonParser.parseString(errorBody).asJsonObject
            val errorObj = json.getAsJsonObject("error")
            val message = errorObj?.get("message")?.asString
            if (!message.isNullOrBlank()) message else "HTTP $statusCode"
        } catch (_: Exception) {
            errorBody.take(150).ifBlank { "HTTP $statusCode" }
        }
    }
}

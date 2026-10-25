// File: app/src/main/java/com/myai/assistant/ai/GeminiClient.kt
// Gemini Client — Google AI Brain (Key rotation + alternating chat history + robust error handling)

package com.myai.assistant.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.myai.assistant.BuildConfig
import com.myai.assistant.ai.models.AiParsedResponse
import com.myai.assistant.ai.models.AiResponseParser
import com.myai.assistant.ai.models.AiSource
import com.myai.assistant.ai.models.SYSTEM_PROMPT
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiClient @Inject constructor() {

    companion object {
        private const val TAG = "GeminiClient"
        const val DEFAULT_MODEL = "gemini-2.0-flash"
    }

    var apiKey: String = BuildConfig.GEMINI_API_KEY  // from local.properties via BuildConfig

    // Key rotation support — multiple keys comma-separated
    private var keyList: MutableList<String> = mutableListOf()
    private var currentKeyIndex = 0
    private var generativeModel: GenerativeModel? = null

    init {
        val initialKey = apiKey.trim()
        if (initialKey.isNotEmpty()) {
            if (initialKey.contains(",")) {
                setApiKeys(initialKey)
            } else {
                keyList.clear()
                keyList.add(initialKey)
                currentKeyIndex = 0
            }
        }
    }

    /** Multiple Gemini keys set karo (comma-separated) */
    @Synchronized
    fun setApiKeys(keysCommaSeparated: String) {
        val parsed = keysCommaSeparated.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (parsed.isNotEmpty()) {
            keyList = parsed.toMutableList()
            currentKeyIndex = 0
            apiKey = keyList[0]
            generativeModel = null
            Log.d(TAG, "Configured ${keyList.size} Gemini API keys for rotation")
        }
    }

    /** Runtime key update */
    @Synchronized
    fun updateApiKey(key: String) {
        val trimmed = key.trim()
        if (trimmed.contains(",")) {
            setApiKeys(trimmed)
        } else {
            apiKey = trimmed
            keyList = if (trimmed.isNotBlank()) mutableListOf(trimmed) else mutableListOf()
            currentKeyIndex = 0
            generativeModel = null
            Log.d(TAG, "Gemini API key updated (length: ${apiKey.length})")
        }
    }

    /** Next key pe rotate karo (quota exhausted / 429 pe) */
    @Synchronized
    private fun rotateToNextKey(): Boolean {
        if (keyList.size <= 1) return false
        currentKeyIndex = (currentKeyIndex + 1) % keyList.size
        val newKey = keyList[currentKeyIndex]
        if (newKey != apiKey) {
            Log.w(TAG, "🔄 Rotating Gemini key to index $currentKeyIndex of ${keyList.size}")
            apiKey = newKey
            generativeModel = null  // Force re-init with new key
            return true
        }
        return false
    }

    private fun isQuotaExhausted(e: Exception): Boolean {
        val msg = e.message?.lowercase().orEmpty()
        return msg.contains("429") ||
                msg.contains("resource_exhausted") ||
                msg.contains("quota") ||
                msg.contains("too many requests") ||
                msg.contains("rate limit")
    }

    /**
     * Gemini model initialize karo
     */
    @Synchronized
    private fun getOrCreateModel(): GenerativeModel {
        if (generativeModel == null) {
            Log.d(TAG, "Creating Gemini GenerativeModel with key index $currentKeyIndex (len: ${apiKey.length})")
            generativeModel = GenerativeModel(
                modelName = DEFAULT_MODEL,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.2f
                    topP = 0.9f
                    maxOutputTokens = 1024
                    responseMimeType = "application/json"
                },
                systemInstruction = content { text(SYSTEM_PROMPT.trimIndent()) }
            )
        }
        return generativeModel!!
    }

    /**
     * AI se chat karo
     */
    suspend fun chat(
        userMessage: String,
        chatHistory: List<Pair<String, String>> = emptyList()
    ): AiParsedResponse = chatInternal(userMessage, chatHistory, retryAttempt = 0)

    private suspend fun chatInternal(
        userMessage: String,
        chatHistory: List<Pair<String, String>>,
        retryAttempt: Int
    ): AiParsedResponse = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            Log.e(TAG, "Gemini API key is blank")
            return@withContext AiResponseParser.errorResponse(
                "Gemini API key set nahi hai. Settings mein jaake daalo.",
                AiSource.GEMINI
            )
        }

        try {
            val model = getOrCreateModel()

            // 1. Filter chat history: Gemini requires "user" and "model" roles only
            val filteredHistory = chatHistory
                .filter { (role, content) ->
                    (role == "user" || role == "assistant" || role == "model") && content.isNotBlank()
                }
                .map { (role, content) ->
                    val geminiRole = if (role == "user") "user" else "model"
                    Pair(geminiRole, content.trim())
                }

            // 2. Ensure strictly alternating roles — merge consecutive same-role messages
            val alternatingHistory = mutableListOf<Pair<String, String>>()
            for ((role, content) in filteredHistory) {
                if (alternatingHistory.isNotEmpty() && alternatingHistory.last().first == role) {
                    val prev = alternatingHistory.removeAt(alternatingHistory.size - 1)
                    alternatingHistory.add(Pair(role, "${prev.second}\n$content"))
                } else {
                    alternatingHistory.add(Pair(role, content))
                }
            }

            // 3. History MUST start with "user"
            val startWithUserHistory = if (alternatingHistory.isNotEmpty() && alternatingHistory.first().first != "user") {
                alternatingHistory.drop(1)
            } else {
                alternatingHistory
            }

            // 4. History MUST end with "model" (because userMessage will be the next "user" turn)
            val finalHistory = if (startWithUserHistory.isNotEmpty() && startWithUserHistory.last().first == "user") {
                startWithUserHistory.dropLast(1)
            } else {
                startWithUserHistory
            }

            Log.d(TAG, "Sending to Gemini ($DEFAULT_MODEL) — history: ${finalHistory.size} turns, prompt: '${userMessage.take(80)}'")

            // Chat session banao with cleaned history
            val chat = model.startChat(
                history = finalHistory.map { (role, textContent) ->
                    content(role = role) {
                        text(textContent)
                    }
                }
            )

            // Message bhejo
            val response = chat.sendMessage(userMessage.trim())
            val responseText = response.text?.trim().orEmpty()

            if (responseText.isBlank()) {
                Log.w(TAG, "Gemini returned blank response")
                return@withContext AiResponseParser.errorResponse(
                    "Gemini se khaali response aaya",
                    AiSource.GEMINI
                )
            }

            Log.d(TAG, "Gemini response received (${responseText.length} chars)")
            AiResponseParser.parse(responseText, AiSource.GEMINI)

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini error: ${e.message}", e)

            // 429 / Quota exhausted — seamless rotation & retry
            if (isQuotaExhausted(e) && retryAttempt < (keyList.size - 1) && rotateToNextKey()) {
                Log.w(TAG, "♻️ Quota exhausted, retrying with rotated key (attempt ${retryAttempt + 1})...")
                return@withContext chatInternal(userMessage, chatHistory, retryAttempt + 1)
            }

            AiResponseParser.errorResponse(
                "Gemini error: ${e.message}",
                AiSource.GEMINI
            )
        }
    }

    /**
     * Kya Gemini available hai (API key set hai)?
     */
    fun isAvailable(): Boolean {
        val available = apiKey.isNotBlank()
        return available
    }
}

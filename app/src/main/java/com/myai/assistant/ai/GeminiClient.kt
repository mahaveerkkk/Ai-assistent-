// File: app/src/main/java/com/myai/assistant/ai/GeminiClient.kt
// Gemini Client — Google AI Brain (FIXED: chat history + error handling)

package com.myai.assistant.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import com.myai.assistant.BuildConfig
import com.myai.assistant.ai.models.*
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

    /** Multiple Gemini keys set karo (comma-separated) */
    fun setApiKeys(keysCommaSeparated: String) {
        keyList = keysCommaSeparated.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()
        if (keyList.isNotEmpty()) {
            currentKeyIndex = 0
            apiKey = keyList[0]
            generativeModel = null
        }
    }

    /** Next key pe rotate karo (quota exhausted pe) */
    private fun rotateToNextKey(): Boolean {
        if (keyList.size <= 1) return false
        currentKeyIndex = (currentKeyIndex + 1) % keyList.size
        val newKey = keyList[currentKeyIndex]
        if (newKey != apiKey) {
            Log.w(TAG, "🔄 Rotating Gemini key to index $currentKeyIndex")
            apiKey = newKey
            generativeModel = null  // Force re-init with new key
            return true
        }
        return false
    }

    private var generativeModel: GenerativeModel? = null

    /**
     * Gemini model initialize karo
     */
    private fun getOrCreateModel(): GenerativeModel {
        if (generativeModel == null) {
            Log.d(TAG, "Creating model with API key length: ${apiKey.length}")
            generativeModel = GenerativeModel(
                modelName = DEFAULT_MODEL,
                apiKey = apiKey,
                generationConfig = generationConfig {
                    temperature = 0.7f
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
     * AI se chat karo — FIXED version
     * - System messages filter out (already in systemInstruction)
     * - Alternating user/model roles ensure karo
     * - Better error handling
     */
    suspend fun chat(
        userMessage: String,
        chatHistory: List<Pair<String, String>> = emptyList()
    ): AiParsedResponse = withContext(Dispatchers.IO) {
        try {
            if (apiKey.isBlank()) {
                Log.e(TAG, "API key is blank!")
                return@withContext AiResponseParser.errorResponse(
                    "Gemini API key set nahi hai. Settings mein jaake daalo.",
                    AiSource.GEMINI
                )
            }

            val model = getOrCreateModel()

            // ═══════════════════════════════════════
            // FIXED: Chat history properly filter karo
            // Gemini mein sirf "user" aur "model" roles valid hain
            // "system" messages skip karo (already in systemInstruction)
            // Consecutive same-role messages merge karo
            // ═══════════════════════════════════════
            val filteredHistory = chatHistory
                .filter { (role, _) -> role == "user" || role == "assistant" }
                .map { (role, content) ->
                    val geminiRole = if (role == "user") "user" else "model"
                    Pair(geminiRole, content)
                }

            // Ensure alternating roles — merge consecutive same-role messages
            val alternatingHistory = mutableListOf<Pair<String, String>>()
            for ((role, content) in filteredHistory) {
                if (alternatingHistory.isNotEmpty() && alternatingHistory.last().first == role) {
                    // Same role — merge with previous
                    val prev = alternatingHistory.removeAt(alternatingHistory.size - 1)
                    alternatingHistory.add(Pair(role, "${prev.second}\n${content}"))
                } else {
                    alternatingHistory.add(Pair(role, content))
                }
            }

            // Gemini requires history to start with "user" role
            val cleanHistory = if (alternatingHistory.isNotEmpty() && alternatingHistory[0].first != "user") {
                alternatingHistory.drop(1)
            } else {
                alternatingHistory
            }

            // Also ensure history ends with "model" (not "user", because we send new user message)
            val finalHistory = if (cleanHistory.isNotEmpty() && cleanHistory.last().first == "user") {
                cleanHistory.dropLast(1)
            } else {
                cleanHistory
            }

            Log.d(TAG, "Sending to Gemini — history: ${finalHistory.size} msgs, new: '$userMessage'")

            // Chat session banao with cleaned history
            val chat = model.startChat(
                history = finalHistory.map { (role, textContent) ->
                    content(role = role) {
                        text(textContent)
                    }
                }
            )

            // Message bhejo
            val response = chat.sendMessage(userMessage)
            val responseText = response.text ?: ""

            Log.d(TAG, "Gemini response: ${responseText.take(200)}")

            if (responseText.isBlank()) {
                return@withContext AiResponseParser.errorResponse(
                    "Gemini se khaali response aaya",
                    AiSource.GEMINI
                )
            }

            // Parse karo
            AiResponseParser.parse(responseText, AiSource.GEMINI)

        } catch (e: Exception) {
            Log.e(TAG, "Gemini error: ${e.message}", e)
            // 429 = quota exhausted — try next key
            val errMsg = e.message ?: ""
            if ((errMsg.contains("429") || errMsg.contains("quota") || errMsg.contains("RESOURCE_EXHAUSTED"))
                && rotateToNextKey()) {
                Log.w(TAG, "♻️ Quota exhausted, retrying with new key...")
                return@withContext chat(userMessage, chatHistory)  // Retry with new key
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
        Log.d(TAG, "isAvailable: $available (key length: ${apiKey.length})")
        return available
    }

    /**
     * API key update karo (runtime pe)
     */
    fun updateApiKey(key: String) {
        apiKey = key
        generativeModel = null  // Re-initialize next call pe
        Log.d(TAG, "API key updated, model reset")
    }
}

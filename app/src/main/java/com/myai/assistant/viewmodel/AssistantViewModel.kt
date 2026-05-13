// File: app/src/main/java/com/myai/assistant/viewmodel/AssistantViewModel.kt
// Assistant ViewModel — Full features: AI + Voice + Contacts + SMS + Accessibility

package com.myai.assistant.viewmodel

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.myai.assistant.accessibility.MyAccessibilityService
import com.myai.assistant.ai.AIClient
import com.myai.assistant.ai.models.AiParsedResponse
import com.myai.assistant.data.model.ChatMessage
import com.myai.assistant.data.model.MessageSender
import com.myai.assistant.data.model.MessageType
import com.myai.assistant.data.repository.ChatRepository
import com.myai.assistant.features.contacts.ContactsHelper
import com.myai.assistant.features.location.LocationHelper
import com.myai.assistant.features.messages.SmsHelper
import com.myai.assistant.features.voice.GeminiTTSManager
import com.myai.assistant.features.voice.VoiceManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isAiThinking: Boolean = false,
    val isListening: Boolean = false,
    val isSpeaking: Boolean = false,
    val isVoiceEnabled: Boolean = true,
    val aiSource: String = "offline",  // ollama, gemini, fallback
    val error: String? = null
)

@HiltViewModel
class AssistantViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: Context,
    private val chatRepository: ChatRepository,
    private val aiClient: AIClient,
    private val voiceManager: VoiceManager,
    private val geminiTts: GeminiTTSManager,
    private val contactsHelper: ContactsHelper,
    private val smsHelper: SmsHelper,
    private val locationHelper: LocationHelper
) : ViewModel() {

    companion object {
        private const val TAG = "AssistantVM"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        // Messages load karo
        viewModelScope.launch {
            chatRepository.getAllMessages().collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }

        // Welcome message + AI availability check
        viewModelScope.launch {
            delay(500)
            if (_uiState.value.messages.isEmpty()) {
                sendWelcomeMessage()
            }
            // Check AI availability — retry if offline
            checkAiAvailability()
            if (_uiState.value.aiSource == "offline") {
                delay(2000) // Wait 2s and retry
                Log.d(TAG, "Retrying AI availability check...")
                checkAiAvailability()
            }
            Log.d(TAG, "AI source: ${_uiState.value.aiSource}")
        }

        // Voice Manager initialize
        initializeVoice()
    }

    // ═══════════════════════════════════════
    // VOICE SETUP
    // ═══════════════════════════════════════

    private fun initializeVoice() {
        // STT initialize
        voiceManager.initialize(appContext)

        // Gemini TTS initialize (with Android TTS fallback)
        geminiTts.initialize(appContext)

        // Voice result → message bhejo
        voiceManager.onResult = { transcript ->
            sendVoiceMessage(transcript)
        }

        voiceManager.onPartialResult = { partial ->
            _uiState.update { it.copy(inputText = partial) }
        }

        voiceManager.onError = { error ->
            _uiState.update { it.copy(isListening = false, error = error) }
        }

        // Speaking state track karo (Gemini TTS)
        viewModelScope.launch {
            geminiTts.isSpeaking.collect { speaking ->
                _uiState.update { it.copy(isSpeaking = speaking) }
            }
        }
    }

    // ═══════════════════════════════════════
    // USER ACTIONS
    // ═══════════════════════════════════════

    fun updateInputText(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        if (text.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(inputText = "", isAiThinking = true) }
            chatRepository.sendUserMessage(text)
            processWithAi(text)
        }
    }

    fun sendVoiceMessage(transcript: String) {
        if (transcript.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(inputText = "", isAiThinking = true, isListening = false) }
            chatRepository.sendUserMessage(transcript, MessageType.VOICE)
            processWithAi(transcript)
        }
    }

    fun toggleListening() {
        val currentlyListening = _uiState.value.isListening
        if (currentlyListening) {
            voiceManager.stopListening()
            _uiState.update { it.copy(isListening = false) }
        } else {
            voiceManager.startListening()
            _uiState.update { it.copy(isListening = true) }
        }
    }

    fun stopListening() {
        voiceManager.stopListening()
        _uiState.update { it.copy(isListening = false) }
    }

    fun toggleVoiceOutput() {
        _uiState.update { it.copy(isVoiceEnabled = !it.isVoiceEnabled) }
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.clearAll()
            sendWelcomeMessage()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // ═══════════════════════════════════════
    // AI CONFIG
    // ═══════════════════════════════════════

    fun setOllamaUrl(url: String) = aiClient.setOllamaUrl(url)
    fun setOllamaModel(model: String) = aiClient.setOllamaModel(model)
    fun setGeminiApiKey(key: String) = aiClient.setGeminiApiKey(key)

    fun setUseOllama(use: Boolean) { aiClient.useLocalOllama = use }
    fun setUseGemini(use: Boolean) { aiClient.useGeminiFallback = use }

    // ═══════════════════════════════════════
    // AI PROCESSING
    // ═══════════════════════════════════════

    private suspend fun processWithAi(userMessage: String) {
        try {
            val loadingId = chatRepository.insertLoadingMessage()

            val currentMessages = _uiState.value.messages
            val response: AiParsedResponse = aiClient.sendMessage(
                userMessage = userMessage,
                chatHistory = currentMessages
            )

            chatRepository.replaceLoadingWithResponse(
                loadingId = loadingId,
                content = response.message,
                actionType = if (response.action != "GENERAL") response.action else null,
                actionData = response.target
            )

            _uiState.update {
                it.copy(isAiThinking = false, aiSource = response.source.name.lowercase())
            }

            // Voice output (agar enabled hai) — Gemini TTS
            if (_uiState.value.isVoiceEnabled && response.message.isNotBlank()) {
                geminiTts.speak(response.message)
            }

            // Action execute karo
            if (response.action != "GENERAL" && response.isSuccess) {
                executeAction(response)
            }

        } catch (e: Exception) {
            _uiState.update { it.copy(isAiThinking = false, error = "Error: ${e.message}") }
            chatRepository.saveErrorMessage("⚠️ ${e.message}")
        }
    }

    /**
     * AI action execute karo — FULL FEATURES
     */
    private suspend fun executeAction(response: AiParsedResponse) {
        val action = response.action.uppercase()
        val target = response.target

        when (action) {
            // 📞 CALL
            "CALL" -> {
                if (target != null) {
                    val success = contactsHelper.callByName(appContext, target)
                    if (!success) contactsHelper.makeCall(appContext, target)
                }
            }

            // 💬 SMS
            "SMS" -> {
                val contact = contactsHelper.findContact(appContext, target ?: "")
                val message = response.data?.get("message")?.toString() ?: ""
                if (contact != null && message.isNotBlank()) {
                    smsHelper.sendSms(appContext, contact.phoneNumber, message)
                    chatRepository.saveSystemMessage("✅ SMS sent to ${contact.name}")
                }
            }

            // 📍 LOCATION
            "LOCATION" -> {
                val location = locationHelper.getCurrentLocation(appContext)
                if (location != null) {
                    chatRepository.saveAiResponse(
                        content = "📍 Aapki location:\n${location.address}\n\n" +
                                "Area: ${location.area}\nCity: ${location.city}\n" +
                                "Coordinates: ${location.latitude}, ${location.longitude}"
                    )
                }
            }

            // Baaki actions Accessibility Service se
            else -> {
                val service = MyAccessibilityService.instance
                if (service != null) {
                    val success = service.executeAiCommand(action, target, response.data)
                    if (!success) {
                        Log.w(TAG, "❌ Action failed: $action")
                    }
                } else if (action in listOf("OPEN_APP", "TYPE_TEXT", "CLICK", "BACK", "HOME", "READ_SCREEN")) {
                    chatRepository.saveSystemMessage("⚠️ Accessibility Service ON karo for this action")
                }
            }
        }
    }

    private suspend fun sendWelcomeMessage() {
        chatRepository.saveAiResponse(
            content = "Namaste! 🙏 Main aapka personal AI assistant hoon.\n\n" +
                    "Mujhse kuch bhi poocho:\n" +
                    "• \"Rahul ko call karo\" 📞\n" +
                    "• \"Mummy ko SMS bhejo\" 💬\n" +
                    "• \"WhatsApp kholo\" 📱\n" +
                    "• \"Photo lo\" 📸\n" +
                    "• \"Meri location batao\" 📍\n\n" +
                    "Voice button dabao ya type karo! 🚀"
        )
    }

    private suspend fun checkAiAvailability() {
        val availability = aiClient.checkAvailability()
        val source = when {
            availability["ollama"] == true -> "ollama"
            availability["gemini"] == true -> "gemini"
            else -> "offline"
        }
        _uiState.update { it.copy(aiSource = source) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.destroy()
        geminiTts.destroy()
    }
}

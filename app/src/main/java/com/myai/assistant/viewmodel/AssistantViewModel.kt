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
import com.myai.assistant.features.device.DeviceInfoManager
import com.myai.assistant.features.device.AlarmHelper
import com.myai.assistant.features.device.CalendarHelper
import com.myai.assistant.features.device.ClipboardHelper
import com.myai.assistant.features.camera.CameraManager
import com.myai.assistant.features.location.LocationHelper
import com.myai.assistant.features.media.MediaHelper
import com.myai.assistant.features.messages.SmsHelper
import com.myai.assistant.features.system.SystemControlManager
import com.myai.assistant.features.voice.GeminiTTSManager
import com.myai.assistant.features.voice.VoiceManager
import com.myai.assistant.data.repository.SettingsRepository
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
    private val assistantBrain: com.myai.assistant.ai.AssistantBrain,
    val settings: SettingsRepository,
    private val systemControl: SystemControlManager,
    private val alarmHelper: AlarmHelper
) : ViewModel() {

    companion object {
        private const val TAG = "AssistantVM"
    }

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Voice intent trigger from widget
    var launchVoicePending = false
    private val _voiceIntentTrigger = kotlinx.coroutines.flow.MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val voiceIntentTrigger = _voiceIntentTrigger.asSharedFlow()

    fun triggerVoiceIntent() {
        _voiceIntentTrigger.tryEmit(Unit)
    }

    fun startListening() {
        if (!_uiState.value.isListening) {
            voiceManager.startListening()
            _uiState.update { it.copy(isListening = true) }
        }
    }

    // Settings flows & setters
    private val _chatTheme = MutableStateFlow(settings.chatTheme)
    val chatTheme = _chatTheme.asStateFlow()

    fun setChatTheme(theme: String) {
        settings.chatTheme = theme
        _chatTheme.value = theme
    }

    private val _continuousVoiceMode = MutableStateFlow(settings.continuousVoiceMode)
    val continuousVoiceMode = _continuousVoiceMode.asStateFlow()

    fun setContinuousVoiceMode(enabled: Boolean) {
        settings.continuousVoiceMode = enabled
        _continuousVoiceMode.value = enabled
    }

    // Quick Actions
    var isFlashlightOn = false
        private set
    fun toggleFlashlight(): Boolean {
        isFlashlightOn = !isFlashlightOn
        val success = systemControl.toggleFlashlight(isFlashlightOn)
        if (!success) {
            isFlashlightOn = !isFlashlightOn
        }
        return success
    }

    fun openWifiPanel(): Boolean {
        return systemControl.toggleWifi(true)
    }

    var isDndOn = false
        private set
    fun toggleDnd(): Boolean {
        isDndOn = !isDndOn
        val success = systemControl.toggleDnd(if (isDndOn) "on" else "off")
        if (!success) {
            isDndOn = !isDndOn
        }
        return success
    }

    var isBluetoothOn = false
        private set
    fun toggleBluetooth(): Boolean {
        isBluetoothOn = !isBluetoothOn
        val success = systemControl.toggleBluetooth(isBluetoothOn)
        if (!success) {
            isBluetoothOn = !isBluetoothOn
        }
        return success
    }

    fun openAlarm(): Boolean {
        return alarmHelper.showAlarms()
    }

    fun takeScreenshot() {
        triggerDiagnosticAction("SCREENSHOT")
    }

    init {
        // Initialize voice settings from repository
        _uiState.update { it.copy(isVoiceEnabled = settings.voiceEnabled) }

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

        // Collect AssistantBrain states
        viewModelScope.launch {
            assistantBrain.isAiThinking.collect { thinking ->
                _uiState.update { it.copy(isAiThinking = thinking) }
            }
        }

        viewModelScope.launch {
            assistantBrain.aiSource.collect { source ->
                _uiState.update { it.copy(aiSource = source) }
            }
        }

        viewModelScope.launch {
            assistantBrain.error.collect { err ->
                _uiState.update { it.copy(error = err) }
            }
        }
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
            _uiState.update { it.copy(inputText = "") }
            chatRepository.sendUserMessage(text)
            assistantBrain.processRequest(text, isVoiceOutputEnabled = _uiState.value.isVoiceEnabled)
        }
    }

    fun sendVoiceMessage(transcript: String) {
        if (transcript.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(inputText = "", isListening = false) }
            chatRepository.sendUserMessage(transcript, MessageType.VOICE)
            assistantBrain.processRequest(transcript, isVoiceOutputEnabled = _uiState.value.isVoiceEnabled)
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
        val newState = !_uiState.value.isVoiceEnabled
        settings.voiceEnabled = newState
        _uiState.update { it.copy(isVoiceEnabled = newState) }
    }

    fun clearChat() {
        viewModelScope.launch {
            chatRepository.clearAll()
            sendWelcomeMessage()
        }
    }

    fun dismissError() {
        assistantBrain.clearError()
    }

    // ═══════════════════════════════════════
    // AI CONFIG
    // ═══════════════════════════════════════

    fun setOllamaUrl(url: String) = aiClient.setOllamaUrl(url)
    fun setOllamaModel(model: String) = aiClient.setOllamaModel(model)
    fun setGeminiApiKey(key: String) = aiClient.setGeminiApiKey(key)

    fun setUseOllama(use: Boolean) { aiClient.useLocalOllama = use }
    fun setUseGemini(use: Boolean) { aiClient.useGeminiFallback = use }

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
        assistantBrain.setAiSource(source)
    }

    /**
     * Diagnostics ke liye action trigger karo
     */
    fun triggerDiagnosticAction(action: String, target: String? = null) {
        viewModelScope.launch {
            val service = MyAccessibilityService.instance
            if (service != null) {
                val success = service.executeAiCommand(action, target, null)
                if (success) {
                    chatRepository.saveSystemMessage("✅ Diagnostics: Successfully executed $action")
                } else {
                    chatRepository.saveSystemMessage("❌ Diagnostics: Failed to execute $action")
                }
            } else {
                chatRepository.saveSystemMessage("⚠️ Diagnostics: Accessibility Service is not active")
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // AGENT LOOP — Multi-step autonomous execution
    // ═══════════════════════════════════════════════════════

    private val agentLoop = com.myai.assistant.ai.AgentLoop(aiClient, chatRepository)

    /**
     * 🔁 Multi-step agent mode — complex tasks ke liye
     * Example: "WhatsApp pe Rahul ko birthday wish karo"
     * Agent will: Open WhatsApp → Search Rahul → Click → Type → Send
     */
    fun executeAgentGoal(goal: String) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isAiThinking = true) }

                val result = agentLoop.executeGoal(
                    userGoal = goal,
                    executeAction = { response ->
                        try {
                            executeActionAndReturn(response)
                        } catch (e: Exception) {
                            Log.e(TAG, "Agent action error: ${e.message}")
                            false
                        }
                    }
                )

                if (result.success) {
                    chatRepository.saveAiResponse(content = "✅ ${result.message}")
                } else {
                    chatRepository.saveSystemMessage("⚠️ Agent: ${result.message} (${result.stepsUsed} steps)")
                }

            } catch (e: Exception) {
                chatRepository.saveSystemMessage("❌ Agent error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isAiThinking = false) }
            }
        }
    }

    /**
     * 🛑 Stop running agent loop
     */
    fun stopAgent() {
        agentLoop.stop()
        viewModelScope.launch {
            chatRepository.saveSystemMessage("🛑 Agent stopped")
        }
    }

    /**
     * Execute action and return success/failure (for agent loop)
     */
    private suspend fun executeActionAndReturn(response: AiParsedResponse): Boolean {
        val action = response.action.uppercase()
        val target = response.target

        return try {
            when (action) {
                "CALL", "SMS", "LOCATION", "DEVICE_INFO", "SET_ALARM", "SET_TIMER",
                "PLAY_MUSIC", "EMAIL", "CALENDAR", "CLIPBOARD", "CAMERA",
                "SCREENSHOT", "READ_NOTIFICATIONS" -> {
                    assistantBrain.executeAction(response)
                    true
                }
                "DONE", "GENERAL" -> true
                else -> {
                    val service = MyAccessibilityService.instance
                    service?.executeAiCommand(action, target, response.data) ?: false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "executeActionAndReturn error: ${e.message}")
            false
        }
    }

    override fun onCleared() {
        super.onCleared()
        agentLoop.stop()
        voiceManager.destroy()
        geminiTts.destroy()
    }
}


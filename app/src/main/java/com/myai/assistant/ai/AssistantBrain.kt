// File: app/src/main/java/com/myai/assistant/ai/AssistantBrain.kt
package com.myai.assistant.ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import com.myai.assistant.accessibility.MyAccessibilityService
import com.myai.assistant.ai.models.AiParsedResponse
import com.myai.assistant.ai.models.AiSource
import com.myai.assistant.data.repository.ChatRepository
import com.myai.assistant.features.camera.CameraManager
import com.myai.assistant.features.contacts.ContactsHelper
import com.myai.assistant.features.device.AlarmHelper
import com.myai.assistant.features.device.CalendarHelper
import com.myai.assistant.features.device.ClipboardHelper
import com.myai.assistant.features.device.DeviceInfoManager
import com.myai.assistant.features.location.LocationHelper
import com.myai.assistant.features.media.MediaHelper
import com.myai.assistant.features.messages.SmsHelper
import com.myai.assistant.features.system.SystemControlManager
import com.myai.assistant.features.voice.GeminiTTSManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssistantBrain @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val chatRepository: ChatRepository,
    private val aiClient: AIClient,
    private val geminiTts: GeminiTTSManager,
    private val contactsHelper: ContactsHelper,
    private val smsHelper: SmsHelper,
    private val locationHelper: LocationHelper,
    private val deviceInfoManager: DeviceInfoManager,
    private val alarmHelper: AlarmHelper,
    private val mediaHelper: MediaHelper,
    private val calendarHelper: CalendarHelper,
    private val clipboardHelper: ClipboardHelper,
    private val cameraManager: CameraManager,
    private val systemControlManager: SystemControlManager
) {
    companion object {
        private const val TAG = "AssistantBrain"

        // Common app package names — direct launch without accessibility
        val APP_PACKAGE_MAP = mapOf(
            "youtube" to "com.google.android.youtube",
            "whatsapp" to "com.whatsapp",
            "whatsapp business" to "com.whatsapp.w4b",
            "wa business" to "com.whatsapp.w4b",
            "instagram" to "com.instagram.android",
            "facebook" to "com.facebook.katana",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "gmail" to "com.google.android.gm",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "chrome" to "com.android.chrome",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "telegram" to "org.telegram.messenger",
            "snapchat" to "com.snapchat.android",
            "settings" to "com.android.settings",
            "calculator" to "com.google.android.calculator",
            "clock" to "com.google.android.deskclock",
            "calendar" to "com.google.android.calendar",
            "photos" to "com.google.android.apps.photos",
            "camera" to "com.google.android.GoogleCamera",
            "play store" to "com.android.vending",
            "playstore" to "com.android.vending",
            "phone" to "com.google.android.dialer",
            "contacts" to "com.google.android.contacts",
            "messages" to "com.google.android.apps.messaging",
            "files" to "com.google.android.apps.nbu.files",
            "drive" to "com.google.android.apps.docs",
            "meet" to "com.google.android.apps.tachyon",
            "zoom" to "us.zoom.videomeetings",
            "paytm" to "net.one97.paytm",
            "phonepe" to "com.phonepe.app",
            "gpay" to "com.google.android.apps.nbu.paisa.user",
            "google pay" to "com.google.android.apps.nbu.paisa.user",
            "amazon" to "in.amazon.mShop.android.shopping",
            "flipkart" to "com.flipkart.android",
            "swiggy" to "in.swiggy.android",
            "zomato" to "com.application.zomato",
            "ola" to "com.olacabs.customer",
            "uber" to "com.ubercab",
            "hotstar" to "in.startv.hotstar",
            "jio cinema" to "com.jio.jioplay",
            "prime video" to "com.amazon.avod.thirdpartyclient",
            "linkedin" to "com.linkedin.android",
            "reddit" to "com.reddit.frontpage",
            "discord" to "com.discord",
            "youtube music" to "com.google.android.apps.youtube.music",
            "yt music" to "com.google.android.apps.youtube.music",
            "snap" to "com.snapchat.android",
            "pinterest" to "com.pinterest",
            "tiktok" to "com.zhiliaoapp.musically",
            "teams" to "com.microsoft.teams",
            "microsoft teams" to "com.microsoft.teams",
            "slack" to "com.Slack",
            "twitch" to "tv.twitch.android.app",
            "paytm business" to "com.paytm.business",
            "phonepe business" to "com.phonepe.app.business",
            "keep" to "com.google.android.keep",
            "google keep" to "com.google.android.keep",
            "keep notes" to "com.google.android.keep",
            "docs" to "com.google.android.apps.docs.editors.docs",
            "google docs" to "com.google.android.apps.docs.editors.docs",
            "sheets" to "com.google.android.apps.docs.editors.sheets",
            "google sheets" to "com.google.android.apps.docs.editors.sheets",
            "slides" to "com.google.android.apps.docs.editors.slides",
            "google slides" to "com.google.android.apps.docs.editors.slides",
            "assistant" to "com.google.android.apps.googleassistant",
            "google assistant" to "com.google.android.apps.googleassistant",
            "truecaller" to "com.truecaller",
            "telegram x" to "org.thunderdog.challegram",
            "firefox" to "org.mozilla.firefox",
            "opera" to "com.opera.browser",
            "edge" to "com.microsoft.emmx",
            "microsoft edge" to "com.microsoft.emmx",
            "disney hotstar" to "in.startv.hotstar",
            "disney" to "in.startv.hotstar",
            "jiotv" to "com.jio.jiotv",
            "jio tv" to "com.jio.jiotv",
            "jiosaavn" to "com.jio.media.jiobeats",
            "saavn" to "com.jio.media.jiobeats",
            "wynk" to "com.bsb.portal.music",
            "wynk music" to "com.bsb.portal.music",
            "gaana" to "com.gaana",
            "bookmyshow" to "com.bt.bms",
            "book my show" to "com.bt.bms",
            "rapido" to "com.rapido.passenger",
            "indrive" to "sinet.startup.inDriver",
            "in driver" to "sinet.startup.inDriver",
            "zepto" to "com.zeptoconsumerapp",
            "blinkit" to "com.grofers.customerapp",
            "grofers" to "com.grofers.customerapp",
            "bigbasket" to "com.bigbasket.mobileapp",
            "big basket" to "com.bigbasket.mobileapp",
            "dunzo" to "com.dunzo.user",
            "meesho" to "com.meesho.supply",
            "nykaa" to "com.fsn.nykaa",
            "nyka" to "com.fsn.nykaa",
            "myntra" to "com.myntra.android",
            "ajio" to "com.ril.ajio",
            "tata neu" to "com.tatadigital.tcp",
            "neu" to "com.tatadigital.tcp",
            "cred" to "com.dreamplug.android.cred",
            "groww" to "com.nextbillion.groww",
            "kite" to "com.zerodha.kitemobile",
            "zerodha" to "com.zerodha.kitemobile",
            "angelone" to "com.msf.angelmobile",
            "angel one" to "com.msf.angelmobile",
            "upstox" to "com.upstox.pro",
            "airtel" to "com.myairtelapp",
            "airtel thanks" to "com.myairtelapp",
            "myjio" to "com.jio.myjio",
            "my jio" to "com.jio.myjio",
            "digilocker" to "com.digilocker.android",
            "digi locker" to "com.digilocker.android",
            "umang" to "in.gov.umang.3in1",
            "umang app" to "in.gov.umang.3in1",
            "chatgpt" to "com.openai.chatgpt",
            "copilot" to "com.microsoft.copilot",
            "gemini" to "com.google.android.apps.bard",
            "character ai" to "ai.character.app"
        )
    }

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _aiSource = MutableStateFlow("offline")
    val aiSource: StateFlow<String> = _aiSource.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun setAiSource(source: String) {
        _aiSource.value = source
    }

    suspend fun processRequest(userMessage: String, isVoiceOutputEnabled: Boolean): String {
        try {
            _isAiThinking.value = true
            _error.value = null

            // Check for zero-latency local shortcuts first
            val shortcutResponse = tryLocalShortcutParser(userMessage)
            if (shortcutResponse != null) {
                Log.d(TAG, "⚡ Shortcut triggered: ${shortcutResponse.action}")
                chatRepository.saveAiResponse(content = shortcutResponse.message)
                _isAiThinking.value = false
                _aiSource.value = "shortcut"
                if (isVoiceOutputEnabled && shortcutResponse.message.isNotBlank()) {
                    geminiTts.speak(shortcutResponse.message)
                }
                executeAction(shortcutResponse)
                return shortcutResponse.message
            }

            val loadingId = chatRepository.insertLoadingMessage()
            val currentMessages = chatRepository.getAllMessages().first()
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

            _isAiThinking.value = false
            _aiSource.value = response.source.name.lowercase()

            if (isVoiceOutputEnabled && response.message.isNotBlank()) {
                geminiTts.speak(response.message)
            }

            if (response.action != "GENERAL" && response.isSuccess) {
                executeAction(response)
            }

            return response.message

        } catch (e: Exception) {
            Log.e(TAG, "Error processing request: ${e.message}", e)
            _isAiThinking.value = false
            _error.value = "Error: ${e.message}"
            chatRepository.saveErrorMessage("⚠️ ${e.message}")
            return "Error: ${e.message}"
        }
    }

    /**
     * ⚡ Zero-Latency Shortcut Parser — Instant offline execution with comprehensive
     * Hinglish, Hindi, and English patterns for Calls, WhatsApp, Torch, Volume, WiFi, Bluetooth, Alarms, Timers.
     */
    private fun tryLocalShortcutParser(message: String): AiParsedResponse? {
        val lower = message.trim().lowercase()

        // ═══════════════════════════════════════════════════════
        // 1. FLASHLIGHT / TORCH
        // ═══════════════════════════════════════════════════════
        if (lower.containsAny(
                "torch on", "torch jalao", "torch jala do", "torch chalao", "torch chalu karo", "torch chalu", "torch on karo",
                "flashlight on", "flashlight jalao", "flashlight chalao", "flashlight chalu karo", "flashlight on karo", "flashlight kholo",
                "light on", "light jalao", "light jala do", "light chalu karo", "light on karo", "batti jalao", "batti jala do", "batti on",
                "turn on torch", "turn on flashlight", "turn on light", "switch on torch", "switch on flashlight", "switch on light"
            )
        ) {
            return AiParsedResponse(action = "FLASHLIGHT", target = "on", message = "🔦 Flashlight ON!", isSuccess = true, source = AiSource.FALLBACK)
        }

        if (lower.containsAny(
                "torch off", "torch band", "torch band karo", "torch bujhao", "torch off karo",
                "flashlight off", "flashlight band", "flashlight band karo", "flashlight bujhao", "flashlight off karo",
                "light off", "light band", "light band karo", "light bujhao", "light off karo", "batti bujhao", "batti band karo", "batti band",
                "turn off torch", "turn off flashlight", "turn off light", "switch off torch", "switch off flashlight", "switch off light"
            )
        ) {
            return AiParsedResponse(action = "FLASHLIGHT", target = "off", message = "🔦 Flashlight OFF!", isSuccess = true, source = AiSource.FALLBACK)
        }

        // ═══════════════════════════════════════════════════════
        // 2. VOLUME
        // ═══════════════════════════════════════════════════════
        if (lower.containsAny(
                "volume up", "volume badhao", "sound badhao", "awaz badhao", "awaaz badhao", "sound up", "awaz up",
                "increase volume", "raise volume", "volume raise", "awaz tej karo", "awaz tez karo", "sound tez karo",
                "sound tej karo", "sound badha do", "volume badha do", "awaz badha do"
            )
        ) {
            return AiParsedResponse(action = "VOLUME", target = "up", message = "🔊 Volume badha diya!", isSuccess = true, source = AiSource.FALLBACK)
        }

        if (lower.containsAny(
                "volume down", "volume kam karo", "volume kam", "sound kam karo", "sound down", "awaz kam karo",
                "awaaz kam karo", "sound kam", "awaz kam", "decrease volume", "lower volume", "awaz dheemi karo",
                "sound dheemi karo", "volume kam kar do", "awaz kam kar do"
            )
        ) {
            return AiParsedResponse(action = "VOLUME", target = "down", message = "🔉 Volume kam kar diya!", isSuccess = true, source = AiSource.FALLBACK)
        }

        if (lower.containsAny("volume full", "volume max", "volume 100", "sound full", "awaz full", "sound max", "max volume", "full volume")) {
            return AiParsedResponse(action = "VOLUME", target = "max", message = "🔊 Volume FULL!", isSuccess = true, source = AiSource.FALLBACK)
        }

        if (lower.containsAny(
                "mute", "silent mode", "volume zero", "volume band karo", "sound band karo", "awaz band karo",
                "chup karo", "phone silent karo", "silent karo"
            )
        ) {
            return AiParsedResponse(action = "VOLUME", target = "mute", message = "🔇 Muted!", isSuccess = true, source = AiSource.FALLBACK)
        }

        if (lower.containsAny("unmute", "un-mute", "unmute karo")) {
            return AiParsedResponse(action = "VOLUME", target = "unmute", message = "🔊 Unmuted!", isSuccess = true, source = AiSource.FALLBACK)
        }

        // ═══════════════════════════════════════════════════════
        // 3. WIFI
        // ═══════════════════════════════════════════════════════
        if (lower.containsAny("wifi on", "wifi open", "wifi kholo", "wifi chalao", "wifi chalu karo", "wifi chalu", "wifi on karo", "turn on wifi", "switch on wifi", "start wifi", "enable wifi")) {
            return AiParsedResponse(action = "WIFI", target = "on", message = "📡 Opening WiFi...", isSuccess = true, source = AiSource.FALLBACK)
        }
        if (lower.containsAny("wifi off", "wifi close", "wifi band", "wifi band karo", "turn off wifi", "switch off wifi", "disable wifi")) {
            return AiParsedResponse(action = "WIFI", target = "off", message = "📡 Closing WiFi...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // ═══════════════════════════════════════════════════════
        // 4. BLUETOOTH
        // ═══════════════════════════════════════════════════════
        if (lower.containsAny("bluetooth on", "bluetooth chalao", "bluetooth chalu karo", "bluetooth chalu", "bluetooth open", "bluetooth kholo", "bluetooth on karo", "turn on bluetooth", "switch on bluetooth", "enable bluetooth")) {
            return AiParsedResponse(action = "BLUETOOTH", target = "on", message = "📶 Bluetooth ON!", isSuccess = true, source = AiSource.FALLBACK)
        }
        if (lower.containsAny("bluetooth off", "bluetooth band", "bluetooth band karo", "turn off bluetooth", "switch off bluetooth", "disable bluetooth")) {
            return AiParsedResponse(action = "BLUETOOTH", target = "off", message = "📶 Bluetooth OFF!", isSuccess = true, source = AiSource.FALLBACK)
        }

        // ═══════════════════════════════════════════════════════
        // 5. CALL AUTOMATION
        // ═══════════════════════════════════════════════════════
        val callRegex1 = Regex("^(?:make a\\s+)?call\\s+(?:to\\s+)?([a-zA-Z0-9+ _-]+)$")
        val callRegex2 = Regex("^([a-zA-Z0-9+ _-]+?)\\s+ko\\s+(?:call|phone)\\s*(?:karo|lagao|milao)?$")
        val callRegex3 = Regex("^phone\\s+(?:to\\s+)?([a-zA-Z0-9+ _-]+)$")
        val dialRegex = Regex("^dial\\s+([0-9+ -]+)$")

        val callMatch = callRegex1.find(lower) ?: callRegex2.find(lower) ?: callRegex3.find(lower) ?: dialRegex.find(lower)
        if (callMatch != null) {
            val rawTarget = callMatch.groupValues[1].trim()
            val cleanTarget = rawTarget.replace(Regex("(?i)\\b(ko|karo|lagao|milao|please)\\b"), "").trim()
            val reservedWords = setOf("log", "logs", "history", "recording", "recorder", "settings", "wifi", "bluetooth", "torch")
            if (cleanTarget.isNotBlank() && !reservedWords.contains(cleanTarget)) {
                return AiParsedResponse(
                    action = "CALL",
                    target = cleanTarget,
                    message = "📞 Calling $cleanTarget...",
                    isSuccess = true,
                    source = AiSource.FALLBACK
                )
            }
        }

        // ═══════════════════════════════════════════════════════
        // 6. WHATSAPP AUTOMATION
        // ═══════════════════════════════════════════════════════
        var waContact: String? = null
        var waMsg: String? = null
        val verbIgnoredWords = setOf("open", "kholo", "chalao", "launch", "start", "download", "install", "search", "play", "find", "show", "get", "status", "call", "voice")

        val waKoPattern = Regex("([a-zA-Z0-9+ _-]+?)\\s+ko\\s+(?:whatsapp|wa)\\s*(?:pe|par)?\\s*(?:message|msg)?\\s*(?:karo|bhejo|bolo)\\s*(?:ki\\s+|that\\s+|:\\s+)?(.+)")
        val waPePattern = Regex("(?:whatsapp|wa)\\s+(?:pe\\s+|par\\s+)?([a-zA-Z0-9+ _-]+?)\\s+(?:ko\\s+)?(?:message|msg|bolo|bhejo|say|tell|write)\\s*(?:ki\\s+|that\\s+|:\\s+)?(.+)")
        val msgOnWaPattern = Regex("(?:send\\s+)?(?:message|msg)\\s+(?:to\\s+)?([a-zA-Z0-9+ _-]+?)\\s+(?:on\\s+)?(?:whatsapp|wa)(?:\\s+|:\\s+|,\\s+)(?:saying\\s+|that\\s+|ki\\s+)?(.+)")
        val waDirectPattern = Regex("(?:whatsapp|wa)\\s+([a-zA-Z0-9+_-]+)\\s+(.+)")

        val waKoMatch = waKoPattern.find(lower)
        val waPeMatch = waPePattern.find(lower)
        val msgOnWaMatch = msgOnWaPattern.find(lower)
        val waDirectMatch = waDirectPattern.find(lower)

        when {
            waKoMatch != null -> {
                val candidate = waKoMatch.groupValues[1].trim()
                if (!verbIgnoredWords.contains(candidate)) {
                    waContact = candidate
                    waMsg = waKoMatch.groupValues[2].trim()
                }
            }
            waPeMatch != null -> {
                val candidate = waPeMatch.groupValues[1].trim()
                if (!verbIgnoredWords.contains(candidate)) {
                    waContact = candidate
                    waMsg = waPeMatch.groupValues[2].trim()
                }
            }
            msgOnWaMatch != null -> {
                val candidate = msgOnWaMatch.groupValues[1].trim()
                if (!verbIgnoredWords.contains(candidate)) {
                    waContact = candidate
                    waMsg = msgOnWaMatch.groupValues[2].trim()
                }
            }
            waDirectMatch != null -> {
                val candidate = waDirectMatch.groupValues[1].trim()
                if (!verbIgnoredWords.contains(candidate)) {
                    waContact = candidate
                    waMsg = waDirectMatch.groupValues[2].trim()
                }
            }
        }

        if (waContact != null && waMsg != null && waContact.isNotBlank() && waMsg.isNotBlank()) {
            return AiParsedResponse(
                action = "WHATSAPP_MSG",
                target = waContact,
                message = "📱 WhatsApp pe '$waContact' ko message: \"$waMsg\"",
                data = mapOf("message" to waMsg),
                isSuccess = true,
                source = AiSource.FALLBACK
            )
        }

        // ═══════════════════════════════════════════════════════
        // 7. TIMERS
        // ═══════════════════════════════════════════════════════
        val timerPattern1 = Regex("(?:set\\s+)?timer\\s+(?:for\\s+|lagao\\s+)?(\\d+)\\s*(min|minute|minutes|sec|second|seconds|ghante|hours|hr)")
        val timerPattern2 = Regex("(\\d+)\\s*(min|minute|minutes|sec|second|seconds|ghante|hours|hr)\\s+(?:ka\\s+)?timer(?:\\s+lagao|\\s+set\\s+karo)?")

        val timerMatch = timerPattern1.find(lower) ?: timerPattern2.find(lower)
        if (timerMatch != null) {
            val amount = timerMatch.groupValues[1].toIntOrNull() ?: 1
            val unit = timerMatch.groupValues[2].lowercase()
            val totalSeconds = when {
                unit.startsWith("h") || unit.startsWith("ghant") -> amount * 3600
                unit.startsWith("s") -> amount
                else -> amount * 60 // minutes
            }
            return AiParsedResponse(
                action = "SET_TIMER",
                target = "$totalSeconds",
                message = "⏱️ Timer set for $amount $unit!",
                data = mapOf("seconds" to totalSeconds, "label" to "MyAI Timer"),
                isSuccess = true,
                source = AiSource.FALLBACK
            )
        }

        // ═══════════════════════════════════════════════════════
        // 8. ALARMS
        // ═══════════════════════════════════════════════════════
        if (lower.contains("alarm") || lower.contains("wake me up")) {
            val alarmRegex1 = Regex("(?:set\\s+)?alarm\\s+(?:for\\s+)?(?:subah|shaam|raat|morning|evening|night)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)?")
            val alarmRegex2 = Regex("(?:subah|shaam|raat|morning|evening|night)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(?:baje)?\\s*(?:ka\\s+)?alarm(?:\\s+lagao|\\s+set\\s+karo)?")
            val alarmRegex3 = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(?:baje)?\\s*(?:ka\\s+)?alarm(?:\\s+lagao|\\s+set\\s+karo)")
            val alarmRegex4 = Regex("alarm\\s+(?:lagao|set\\s+karo)\\s*(?:subah|shaam|raat)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm|baje)?")
            val alarmRegex5 = Regex("wake\\s+me\\s+up\\s+(?:at\\s+)?(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?")

            val alarmMatch = alarmRegex1.find(lower) ?: alarmRegex2.find(lower) ?: alarmRegex3.find(lower) ?: alarmRegex4.find(lower) ?: alarmRegex5.find(lower)
            if (alarmMatch != null) {
                var hour = alarmMatch.groupValues[1].toIntOrNull() ?: 7
                val minute = alarmMatch.groupValues[2].toIntOrNull() ?: 0
                val meridian = if (alarmMatch.groupValues.size > 3) alarmMatch.groupValues[3].lowercase() else ""

                val isPm = meridian == "pm" || lower.containsAny("shaam", "raat", "evening", "night", "dopahar")
                val isAm = meridian == "am" || lower.containsAny("subah", "morning")

                if (isPm && hour in 1..11) {
                    hour += 12
                } else if (isAm && hour == 12) {
                    hour = 0
                }

                val timeFormatted = String.format("%02d:%02d", hour, minute)
                return AiParsedResponse(
                    action = "SET_ALARM",
                    target = timeFormatted,
                    message = "⏰ Alarm set for $timeFormatted!",
                    data = mapOf("hour" to hour, "minute" to minute, "label" to "MyAI Alarm"),
                    isSuccess = true,
                    source = AiSource.FALLBACK
                )
            }
        }

        // ═══════════════════════════════════════════════════════
        // 9. LOCK
        // ═══════════════════════════════════════════════════════
        if (lower.containsAny("lock screen", "lock phone", "screen lock", "phone lock", "lock karo", "screen band karo")) {
            return AiParsedResponse(action = "LOCK", target = null, message = "🔒 Locking device...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // ═══════════════════════════════════════════════════════
        // 10. DEVICE INFO
        // ═══════════════════════════════════════════════════════
        if (lower.containsAny("battery", "storage info", "ram info", "device info", "battery kitni hai", "storage kitni hai")) {
            return AiParsedResponse(action = "DEVICE_INFO", target = null, message = "📱 Fetching device info...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // ═══════════════════════════════════════════════════════
        // 11. SCREENSHOT
        // ═══════════════════════════════════════════════════════
        if (lower.containsAny("take screenshot", "screenshot lo", "screenshot khincho", "screenshot")) {
            return AiParsedResponse(action = "SCREENSHOT", target = null, message = "📷 Taking screenshot...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // ═══════════════════════════════════════════════════════
        // 12. CAMERA
        // ═══════════════════════════════════════════════════════
        if (lower.containsAny("open camera", "camera kholo", "camera chalao", "photo lo", "picture lo")) {
            return AiParsedResponse(action = "CAMERA", target = null, message = "📸 Opening Camera...", isSuccess = true, source = AiSource.FALLBACK)
        }

        // ═══════════════════════════════════════════════════════
        // 13. DND
        // ═══════════════════════════════════════════════════════
        if (lower.containsAny("dnd on", "do not disturb on", "dnd chalao", "dnd chalu karo", "dnd chalu")) {
            return AiParsedResponse(action = "DND", target = "on", message = "🔇 DND ON!", isSuccess = true, source = AiSource.FALLBACK)
        }
        if (lower.containsAny("dnd off", "do not disturb off", "dnd band karo", "dnd band")) {
            return AiParsedResponse(action = "DND", target = "off", message = "🔔 DND OFF!", isSuccess = true, source = AiSource.FALLBACK)
        }

        // ═══════════════════════════════════════════════════════
        // 14. YOUTUBE SEARCH
        // ═══════════════════════════════════════════════════════
        val ytRegex = Regex("(?:youtube|yt).*(?:search|play|dekho|dhundho|chalao)\\s+(.+)")
        val ytMatch = ytRegex.find(lower)
        if (ytMatch != null) {
            val query = ytMatch.groupValues[1].trim()
            if (query.isNotBlank()) {
                return AiParsedResponse(
                    action = "YOUTUBE_SEARCH",
                    target = query,
                    message = "▶️ YouTube pe '$query' search...",
                    isSuccess = true,
                    source = AiSource.FALLBACK
                )
            }
        }

        // ═══════════════════════════════════════════════════════
        // 15. OPEN APP
        // ═══════════════════════════════════════════════════════
        val openAppRegex = Regex("(?:open|kholo|chalao|launch|start)\\s+([a-zA-Z0-9 ]+)")
        val appMatch = openAppRegex.find(lower)
        if (appMatch != null) {
            val appName = appMatch.groupValues[1].trim()
            val systemActions = setOf("wifi", "bluetooth", "camera", "flashlight", "torch", "alarm", "timer", "dnd")
            if (appName.isNotBlank() && !appName.containsAny(*systemActions.toTypedArray())) {
                return AiParsedResponse(
                    action = "OPEN_APP",
                    target = appName,
                    message = "🚀 Opening $appName...",
                    isSuccess = true,
                    source = AiSource.FALLBACK
                )
            }
        }

        return null
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }

    /**
     * Execute AI action with fail-safe error handling and descriptive user feedback
     */
    suspend fun executeAction(response: AiParsedResponse) {
        val action = response.action.uppercase()
        val target = response.target

        try {
            when (action) {

                // 📞 CALL
                "CALL" -> {
                    if (!target.isNullOrBlank()) {
                        val isNumber = target.matches(Regex("^[+]*[0-9 -]{5,16}$"))
                        if (isNumber) {
                            chatRepository.saveSystemMessage("📞 Calling $target...")
                            val success = contactsHelper.makeCall(appContext, target)
                            if (!success) chatRepository.saveSystemMessage("❌ Could not place call to $target")
                        } else {
                            val contact = contactsHelper.findContact(appContext, target)
                            if (contact != null) {
                                chatRepository.saveSystemMessage("📞 Calling ${contact.name} (${contact.phoneNumber})...")
                                val success = contactsHelper.makeCall(appContext, contact.phoneNumber)
                                if (!success) chatRepository.saveSystemMessage("❌ Call failed")
                            } else {
                                chatRepository.saveSystemMessage("⚠️ Contact '$target' not found in contacts. Opening dialer...")
                                contactsHelper.openDialer(appContext, target)
                            }
                        }
                    } else {
                        chatRepository.saveSystemMessage("⚠️ Please specify a contact or number to call.")
                    }
                }

                // 📱 WHATSAPP DIRECT MESSAGE
                "WHATSAPP_MSG" -> {
                    val contact = target?.trim() ?: ""
                    val msg = response.data?.get("message")?.toString()?.trim() ?: ""
                    if (contact.isBlank()) {
                        chatRepository.saveSystemMessage("⚠️ Contact name or number missing for WhatsApp")
                        return
                    }

                    try {
                        val isRawNumber = contact.matches(Regex("^[+]*[0-9 -]{7,16}$"))
                        val foundContact = if (!isRawNumber) contactsHelper.findContact(appContext, contact) else null
                        val targetNumber = if (isRawNumber) contact else foundContact?.phoneNumber

                        if (targetNumber != null) {
                            val formattedNumber = contactsHelper.formatForWhatsApp(targetNumber)
                            val displayName = foundContact?.name ?: contact
                            chatRepository.saveSystemMessage("📞 Found '$displayName' ($formattedNumber). Opening WhatsApp...")

                            val uri = Uri.parse("https://wa.me/$formattedNumber?text=${Uri.encode(msg)}")
                            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                                setPackage("com.whatsapp")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }

                            try {
                                appContext.startActivity(intent)
                            } catch (e: Exception) {
                                val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                appContext.startActivity(fallbackIntent)
                            }

                            // Auto-send via Accessibility if active
                            val service = MyAccessibilityService.instance
                            if (service != null) {
                                CoroutineScope(Dispatchers.Main).launch {
                                    val sent = service.appAutomator.autoClickSendButton(maxAttempts = 12)
                                    if (sent) {
                                        chatRepository.saveSystemMessage("✅ WhatsApp message sent to '$displayName'")
                                    } else {
                                        chatRepository.saveSystemMessage("📱 WhatsApp opened. Please tap Send.")
                                    }
                                }
                            } else {
                                chatRepository.saveSystemMessage("✅ WhatsApp opened. Tap Send to dispatch.")
                            }
                        } else {
                            // Contact not in address book; use Accessibility Service to automate WhatsApp UI search
                            val service = MyAccessibilityService.instance
                            if (service != null) {
                                chatRepository.saveSystemMessage("📱 Searching '$contact' in WhatsApp...")
                                CoroutineScope(Dispatchers.Main).launch {
                                    val ok = service.appAutomator.sendWhatsAppMessage(contact, msg)
                                    chatRepository.saveSystemMessage(
                                        if (ok) "✅ Message sent to '$contact' via WhatsApp"
                                        else "⚠️ Could not find '$contact' in WhatsApp"
                                    )
                                }
                            } else {
                                chatRepository.saveSystemMessage("⚠️ Contact '$contact' not found. Opening WhatsApp...")
                                val intent = appContext.packageManager.getLaunchIntentForPackage("com.whatsapp")?.apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                if (intent != null) {
                                    appContext.startActivity(intent)
                                } else {
                                    chatRepository.saveSystemMessage("❌ WhatsApp is not installed.")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "WhatsApp action failed: ${e.message}", e)
                        chatRepository.saveSystemMessage("❌ WhatsApp error: ${e.message}")
                    }
                }

                // 💬 SMS
                "SMS" -> {
                    if (!target.isNullOrBlank()) {
                        val isNumber = target.matches(Regex("^[+]*[0-9 -]{5,16}$"))
                        val destination = if (isNumber) target else contactsHelper.findContact(appContext, target)?.phoneNumber
                        val msg = response.data?.get("message")?.toString() ?: ""
                        if (destination != null) {
                            val success = smsHelper.sendSms(appContext, destination, msg)
                            chatRepository.saveSystemMessage(
                                if (success) "✅ SMS sent to $target"
                                else "📱 SMS app opened for $target"
                            )
                        } else {
                            chatRepository.saveSystemMessage("❌ Contact '$target' not found for SMS")
                        }
                    } else {
                        chatRepository.saveSystemMessage("⚠️ Please specify a contact or number for SMS")
                    }
                }

                // 🔦 FLASHLIGHT
                "FLASHLIGHT" -> {
                    val enable = target?.lowercase()?.trim() in setOf("on", "true", "chalu", "jalao", "start")
                    val success = systemControlManager.toggleFlashlight(enable)
                    chatRepository.saveSystemMessage(
                        if (success) "🔦 Flashlight ${if (enable) "ON" else "OFF"}"
                        else "❌ Flashlight toggle failed (Camera might be in use or unavailable)"
                    )
                }

                // 📡 WIFI
                "WIFI" -> {
                    val enable = target?.lowercase()?.trim() in setOf("on", "true", "chalu", "start")
                    val success = systemControlManager.toggleWifi(enable)
                    chatRepository.saveSystemMessage(
                        if (success) "📡 WiFi ${if (enable) "ON / Settings opened" else "OFF / Settings opened"}"
                        else "❌ Failed to toggle WiFi"
                    )
                }

                // 📶 BLUETOOTH
                "BLUETOOTH" -> {
                    val enable = target?.lowercase()?.trim() in setOf("on", "true", "chalu", "start")
                    val success = systemControlManager.toggleBluetooth(enable)
                    chatRepository.saveSystemMessage(
                        if (success) "📶 Bluetooth ${if (enable) "ON" else "OFF"}"
                        else "❌ Bluetooth toggle failed (Opening Bluetooth settings...)"
                    )
                }

                // 🔊 VOLUME
                "VOLUME" -> {
                    val dir = target?.lowercase()?.trim() ?: "up"
                    val success = systemControlManager.adjustVolume(dir)
                    chatRepository.saveSystemMessage(
                        if (success) "🔊 Volume: $dir"
                        else "❌ Failed to adjust volume"
                    )
                }

                // 🔇 DND
                "DND" -> {
                    val mode = target?.lowercase()?.trim() ?: "silent"
                    val success = systemControlManager.toggleDnd(mode)
                    chatRepository.saveSystemMessage(
                        if (success) "🔇 DND / Ringer mode: $mode"
                        else "❌ DND failed (Check Do Not Disturb permissions)"
                    )
                }

                // ⏰ SET ALARM
                "SET_ALARM" -> {
                    val hour = response.data?.get("hour")?.toString()?.toIntOrNull() ?: 7
                    val minute = response.data?.get("minute")?.toString()?.toIntOrNull() ?: 0
                    val label = response.data?.get("label")?.toString() ?: "MyAI Alarm"
                    val success = alarmHelper.setAlarm(hour, minute, label)
                    val timeStr = String.format("%02d:%02d", hour, minute)
                    chatRepository.saveSystemMessage(
                        if (success) "✅ Alarm set for $timeStr ($label)"
                        else "❌ Failed to set alarm"
                    )
                }

                // ⏱️ SET TIMER
                "SET_TIMER" -> {
                    val seconds = response.data?.get("seconds")?.toString()?.toIntOrNull() ?: 60
                    val label = response.data?.get("label")?.toString() ?: "MyAI Timer"
                    val success = alarmHelper.setTimer(seconds, label)
                    val durationDesc = if (seconds >= 60) "${seconds / 60} min ${seconds % 60} sec".trim() else "${seconds}s"
                    chatRepository.saveSystemMessage(
                        if (success) "✅ Timer set for $durationDesc"
                        else "❌ Failed to set timer"
                    )
                }

                // 📱 DEVICE INFO
                "DEVICE_INFO" -> {
                    val summary = deviceInfoManager.getFullSummary()
                    chatRepository.saveAiResponse(content = summary)
                }

                // 📍 LOCATION
                "LOCATION" -> {
                    val location = locationHelper.getCurrentLocation(appContext)
                    if (location != null) {
                        chatRepository.saveAiResponse(
                            content = "📍 Location:\n${location.address}\nCity: ${location.city}\n(${location.latitude}, ${location.longitude})"
                        )
                    } else {
                        chatRepository.saveSystemMessage("⚠️ Could not retrieve current location")
                    }
                }

                // 🎵 PLAY MUSIC
                "PLAY_MUSIC" -> {
                    if (target != null) {
                        val success = mediaHelper.playMusic(target)
                        chatRepository.saveSystemMessage(if (success) "🎵 Playing: $target" else "❌ Music play failed")
                    }
                }

                // 📧 EMAIL
                "EMAIL" -> {
                    val to = response.data?.get("to")?.toString() ?: ""
                    val subject = response.data?.get("subject")?.toString() ?: ""
                    val body = response.data?.get("body")?.toString() ?: ""
                    mediaHelper.composeEmail(to, subject, body)
                    chatRepository.saveSystemMessage("📧 Email compose opened")
                }

                // 📅 CALENDAR
                "CALENDAR" -> {
                    val eventTitle = response.data?.get("title")?.toString()
                    if (eventTitle != null) {
                        calendarHelper.createEvent(title = eventTitle)
                        chatRepository.saveSystemMessage("📅 Event created: $eventTitle")
                    } else {
                        val events = calendarHelper.getUpcomingEvents()
                        if (events.isNotEmpty()) {
                            val list = events.take(5).joinToString("\n") { "• ${it.title}" }
                            chatRepository.saveAiResponse(content = "📅 Upcoming:\n$list")
                        } else {
                            chatRepository.saveAiResponse(content = "📅 No upcoming events")
                        }
                    }
                }

                // 📋 CLIPBOARD
                "CLIPBOARD" -> {
                    val copyText = response.data?.get("text")?.toString()
                    if (copyText != null) {
                        clipboardHelper.copyText(copyText)
                        chatRepository.saveSystemMessage("📋 Copied!")
                    } else {
                        val current = clipboardHelper.getClipboardText()
                        chatRepository.saveAiResponse(content = if (current.isNotBlank()) "📋 Clipboard: $current" else "📋 Clipboard empty")
                    }
                }

                // 📸 CAMERA
                "CAMERA" -> {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    appContext.startActivity(intent)
                    chatRepository.saveSystemMessage("📸 Camera opened!")
                }

                // ▶️ YOUTUBE SEARCH
                "YOUTUBE_SEARCH" -> {
                    val query = target ?: ""
                    try {
                        val intent = Intent(Intent.ACTION_SEARCH).apply {
                            setPackage("com.google.android.youtube")
                            putExtra("query", query)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        appContext.startActivity(intent)
                        chatRepository.saveSystemMessage("▶️ YouTube searching: $query")
                    } catch (e: Exception) {
                        try {
                            val uri = Uri.parse("https://www.youtube.com/results?search_query=${Uri.encode(query)}")
                            appContext.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                            chatRepository.saveSystemMessage("▶️ YouTube (browser): $query")
                        } catch (ex: Exception) {
                            chatRepository.saveSystemMessage("❌ YouTube error")
                        }
                    }
                }

                // 🔔 READ NOTIFICATIONS
                "READ_NOTIFICATIONS" -> {
                    val notifications = com.myai.assistant.service.MyNotificationListener.notifications.value
                    if (notifications.isNotEmpty()) {
                        val list = notifications.take(5).joinToString("\n\n") { n ->
                            "📱 ${n.appName}\n📌 ${n.title}\n💬 ${n.text}"
                        }
                        chatRepository.saveAiResponse(content = "🔔 Notifications:\n\n$list")
                    } else {
                        chatRepository.saveAiResponse(content = "🔔 No recent notifications")
                    }
                }

                // 🔍 OCR READ
                "OCR_READ" -> {
                    val cacheDir = appContext.cacheDir
                    val latestImage = cacheDir.listFiles { f -> f.name.endsWith(".jpg") || f.name.endsWith(".png") }
                        ?.maxByOrNull { it.lastModified() }
                    if (latestImage != null) {
                        val uri = Uri.fromFile(latestImage)
                        val ocrText = cameraManager.recognizeText(appContext, uri)
                        chatRepository.saveAiResponse(
                            content = if (ocrText.isNotBlank()) "📝 OCR:\n\n$ocrText" else "🔍 No text found"
                        )
                    } else {
                        chatRepository.saveSystemMessage("⚠️ Take screenshot first, then run OCR")
                    }
                }

                // 🗺️ NAVIGATE
                "NAVIGATE" -> {
                    if (target != null) {
                        val uri = Uri.parse("google.navigation:q=${Uri.encode(target)}")
                        appContext.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        chatRepository.saveSystemMessage("🗺️ Navigating to: $target")
                    }
                }

                // 🏪 INSTALL APP
                "INSTALL_APP" -> {
                    if (target != null) {
                        val uri = Uri.parse("market://search?q=${Uri.encode(target)}")
                        appContext.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                        chatRepository.saveSystemMessage("🏪 Play Store: $target")
                    }
                }

                // 📱 OPEN APP — direct package intent
                "OPEN_APP" -> {
                    val appName = target?.lowercase()?.trim() ?: ""
                    val packageName = APP_PACKAGE_MAP[appName]
                        ?: APP_PACKAGE_MAP.entries.firstOrNull { appName.contains(it.key) }?.value

                    if (packageName != null) {
                        val intent = appContext.packageManager.getLaunchIntentForPackage(packageName)?.apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        if (intent != null) {
                            appContext.startActivity(intent)
                            chatRepository.saveSystemMessage("✅ $target opened!")
                        } else {
                            chatRepository.saveSystemMessage("❌ $target not installed")
                        }
                    } else {
                        val service = MyAccessibilityService.instance
                        if (service != null) {
                            service.executeAiCommand(action, target, response.data)
                        } else {
                            try {
                                val uri = Uri.parse("market://search?q=${Uri.encode(appName)}")
                                appContext.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                })
                                chatRepository.saveSystemMessage("🔍 Searching '$appName' on Play Store")
                            } catch (e: Exception) {
                                chatRepository.saveSystemMessage("❌ App not found: $appName")
                            }
                        }
                    }
                }

                // 🔒 LOCK SCREEN
                "LOCK" -> {
                    val service = MyAccessibilityService.instance
                    if (service != null) {
                        service.actionPerformer.lockScreen()
                        chatRepository.saveSystemMessage("🔒 Screen locked")
                    } else {
                        chatRepository.saveSystemMessage("⚠️ Enable Accessibility service to lock screen")
                    }
                }

                // 📷 SCREENSHOT
                "SCREENSHOT" -> {
                    val service = MyAccessibilityService.instance
                    if (service != null) {
                        val success = service.executeAiCommand("SCREENSHOT", null, null)
                        chatRepository.saveSystemMessage(if (success) "📷 Screenshot taken!" else "❌ Screenshot failed")
                    } else {
                        chatRepository.saveSystemMessage("⚠️ Enable Accessibility Service for screenshots")
                    }
                }

                // All other actions via Accessibility Service
                else -> {
                    val service = MyAccessibilityService.instance
                    if (service != null) {
                        val success = service.executeAiCommand(action, target, response.data)
                        if (!success) chatRepository.saveSystemMessage("❌ Action '$action' failed")
                    } else {
                        chatRepository.saveSystemMessage("⚠️ Enable Accessibility Service for action: $action")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing action $action: ${e.message}", e)
            chatRepository.saveSystemMessage("❌ Action error ($action): ${e.message}")
        }
    }

}

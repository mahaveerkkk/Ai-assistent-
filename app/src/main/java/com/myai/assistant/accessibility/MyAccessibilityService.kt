// File: app/src/main/java/com/myai/assistant/accessibility/MyAccessibilityService.kt
// Main Accessibility Service — SABSE POWERFUL feature
// Screen reading, UI automation, event handling

package com.myai.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * MyAccessibilityService — App ka sabse powerful hissa
 *
 * Kya kar sakta hai:
 * - Screen pe kya hai woh padh sakta hai (ScreenReader)
 * - Kisi bhi app ke buttons click kar sakta hai (ActionPerformer)
 * - Text fields mein type kar sakta hai
 * - Swipe / scroll kar sakta hai
 * - Apps ko automate kar sakta hai (WhatsApp, YouTube, etc.)
 * - Notifications detect kar sakta hai
 * - Current app ka naam jaan sakta hai
 */
class MyAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "MyAccessibilityService"

        // Singleton instance — baaki app se access karne ke liye
        private var _instance: MyAccessibilityService? = null
        val instance: MyAccessibilityService? get() = _instance

        fun isRunning(): Boolean = _instance != null

        // Current screen state — observable
        private val _currentApp = MutableStateFlow("")
        val currentApp: StateFlow<String> = _currentApp.asStateFlow()

        private val _screenText = MutableStateFlow("")
        val screenText: StateFlow<String> = _screenText.asStateFlow()

        private val _lastNotification = MutableStateFlow("")
        val lastNotification: StateFlow<String> = _lastNotification.asStateFlow()
    }

    // Sub-modules
    lateinit var actionPerformer: ActionPerformer
        private set
    lateinit var appAutomator: AppAutomator
        private set

    // Coroutine scope for async operations
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Event throttling — bohut zyada events aate hain, sab process nahi karna
    private var lastEventTime = 0L
    private val EVENT_THROTTLE_MS = 300L

    // ═══════════════════════════════════════════════════════
    // LIFECYCLE
    // ═══════════════════════════════════════════════════════

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "✅ Accessibility Service CONNECTED!")

        _instance = this

        // Sub-modules initialize karo
        actionPerformer = ActionPerformer(this)
        appAutomator = AppAutomator(this, actionPerformer)

        // Service config programmatically set karo (XML config ke upar)
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = 100
        }

        Log.d(TAG, "Service configured — FULL POWER mode! 🔥")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "❌ Accessibility Service DESTROYED")
        _instance = null
        serviceScope.cancel()
    }

    override fun onInterrupt() {
        Log.w(TAG, "⚠️ Accessibility Service INTERRUPTED")
    }

    // ═══════════════════════════════════════════════════════
    // EVENT HANDLING — Screen events receive karo
    // ═══════════════════════════════════════════════════════

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Throttle — bohut zyada events ignore karo
        val now = System.currentTimeMillis()
        if (now - lastEventTime < EVENT_THROTTLE_MS) return
        lastEventTime = now

        when (event.eventType) {
            // App badla (window switch)
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowChanged(event)
            }

            // Screen content badla
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                handleContentChanged(event)
            }

            // Kuch click hua
            AccessibilityEvent.TYPE_VIEW_CLICKED -> {
                handleViewClicked(event)
            }

            // Text change hua
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                handleTextChanged(event)
            }

            // Notification aayi
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED -> {
                handleNotification(event)
            }

            // View focused
            AccessibilityEvent.TYPE_VIEW_FOCUSED -> {
                // Focus tracking (optional)
            }

            // Scrolling
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                // Scroll tracking (optional)
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // EVENT HANDLERS
    // ═══════════════════════════════════════════════════════

    /**
     * Window/App changed — user ne nayi app kholi
     */
    private fun handleWindowChanged(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        // System UI ignore karo
        if (packageName == "com.android.systemui") return

        _currentApp.value = packageName
        Log.d(TAG, "📱 App changed: $packageName ($className)")

        // Screen text update karo (async)
        serviceScope.launch {
            updateScreenText()
        }
    }

    /**
     * Content changed — screen pe kuch badla
     */
    private fun handleContentChanged(event: AccessibilityEvent) {
        // Screen text periodically update karo
        serviceScope.launch {
            updateScreenText()
        }
    }

    /**
     * View clicked
     */
    private fun handleViewClicked(event: AccessibilityEvent) {
        val text = event.text?.joinToString(" ") ?: ""
        val desc = event.contentDescription?.toString() ?: ""
        if (text.isNotBlank() || desc.isNotBlank()) {
            Log.d(TAG, "🔘 Clicked: ${text.ifBlank { desc }}")
        }
    }

    /**
     * Text changed in a field
     */
    private fun handleTextChanged(event: AccessibilityEvent) {
        val text = event.text?.joinToString(" ") ?: ""
        Log.d(TAG, "✏️ Text changed: $text")
    }

    /**
     * Notification aayi
     */
    private fun handleNotification(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: ""
        val text = event.text?.joinToString(" ") ?: ""

        if (text.isNotBlank()) {
            _lastNotification.value = "[$packageName] $text"
            Log.d(TAG, "🔔 Notification: [$packageName] $text")
        }
    }

    // ═══════════════════════════════════════════════════════
    // PUBLIC API — Baaki app se yeh functions call karo
    // ═══════════════════════════════════════════════════════

    /**
     * Screen ka poora text extract karo
     */
    fun readScreen(): String {
        val root = rootInActiveWindow ?: return ""
        return ScreenReader.extractScreenText(root)
    }

    /**
     * Screen ka structured snapshot lo
     */
    fun getScreenSnapshot(): List<UiElement> {
        val root = rootInActiveWindow ?: return emptyList()
        return ScreenReader.getScreenSnapshot(root)
    }

    /**
     * Screen text update karo (StateFlow mein)
     */
    private fun updateScreenText() {
        try {
            val root = rootInActiveWindow ?: return
            val text = ScreenReader.extractScreenText(root)
            if (text.isNotBlank()) {
                _screenText.value = text
            }
        } catch (e: Exception) {
            Log.w(TAG, "Screen text update failed: ${e.message}")
        }
    }

    /**
     * AI command execute karo
     * ViewModel se call hoga jab AI koi action bole
     */
    suspend fun executeAiCommand(
        action: String,
        target: String?,
        data: Map<String, Any>?
    ): Boolean {
        Log.d(TAG, "🤖 Executing AI command: $action, target: $target")

        return try {
            when (action.uppercase()) {
                "OPEN_APP" -> {
                    target?.let { appAutomator.openApp(it) } ?: false
                }
                "TYPE_TEXT" -> {
                    target?.let { actionPerformer.typeInFirstField(it) } ?: false
                }
                "CLICK" -> {
                    target?.let { actionPerformer.clickByText(it) } ?: false
                }
                "BACK" -> actionPerformer.pressBack()
                "HOME" -> actionPerformer.pressHome()
                "RECENTS" -> actionPerformer.openRecents()
                "SCROLL_DOWN" -> actionPerformer.swipeUp()
                "SCROLL_UP" -> actionPerformer.swipeDown()
                "SCREENSHOT" -> actionPerformer.takeScreenshot()
                "READ_SCREEN" -> {
                    updateScreenText()
                    true
                }
                "REPLY_CHAT" -> {
                    val app = target ?: return false
                    val contact = data?.get("contact")?.toString() ?: return false
                    val message = data["message"]?.toString() ?: return false

                    if (app.contains("whatsapp", ignoreCase = true)) {
                        appAutomator.sendWhatsAppMessage(contact, message)
                    } else {
                        false
                    }
                }
                "SEARCH" -> {
                    val query = target ?: return false
                    appAutomator.searchGoogle(query)
                }
                "NOTIFICATIONS" -> actionPerformer.openNotifications()
                "FLASHLIGHT" -> {
                    // TODO: Flashlight toggle via CameraManager
                    false
                }
                else -> {
                    Log.w(TAG, "Unknown action: $action")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: ${e.message}", e)
            false
        }
    }
}

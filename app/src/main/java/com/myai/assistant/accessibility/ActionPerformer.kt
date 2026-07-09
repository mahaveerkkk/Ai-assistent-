// File: app/src/main/java/com/myai/assistant/accessibility/ActionPerformer.kt
// Action Performer — Screen pe actions perform karo (click, type, swipe, scroll)

package com.myai.assistant.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.delay

/**
 * Action Performer — Screen pe koi bhi action le sakta hai
 * Click, type, swipe, scroll, global actions (back, home, recents)
 */
class ActionPerformer(
    private val service: AccessibilityService
) {
    companion object {
        private const val TAG = "ActionPerformer"
    }

    // ═══════════════════════════════════════════════════════
    // GLOBAL ACTIONS — Back, Home, Recents, etc.
    // ═══════════════════════════════════════════════════════

    /** Back button press karo */
    fun pressBack(): Boolean {
        Log.d(TAG, "Pressing BACK")
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    /** Home button press karo */
    fun pressHome(): Boolean {
        Log.d(TAG, "Pressing HOME")
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
    }

    /** Recent apps kholo */
    fun openRecents(): Boolean {
        Log.d(TAG, "Opening RECENTS")
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

    /** Notification panel kholo */
    fun openNotifications(): Boolean {
        Log.d(TAG, "Opening NOTIFICATIONS")
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
    }

    /** Quick settings kholo */
    fun openQuickSettings(): Boolean {
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
    }

    /** Power dialog dikhao */
    fun showPowerDialog(): Boolean {
        return service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
    }

    /** Screenshot lo (API 28+) */
    fun takeScreenshot(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
        } else false
    }

    /** Lock screen (API 28+) */
    fun lockScreen(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN)
        } else false
    }

    // ═══════════════════════════════════════════════════════
    // NODE ACTIONS — Click, Type, Scroll on specific elements
    // ═══════════════════════════════════════════════════════

    /**
     * Element pe click karo
     */
    fun clickNode(node: AccessibilityNodeInfo): Boolean {
        Log.d(TAG, "Clicking: ${node.text ?: node.contentDescription}")

        // Pehle direct click try karo
        if (node.isClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        // Agar node clickable nahi hai, parent pe try karo
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) {
                return parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            parent = parent.parent
        }

        // Last resort — gesture click
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return tapAtCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    /**
     * Text field mein text type karo
     */
    fun typeText(node: AccessibilityNodeInfo, text: String): Boolean {
        Log.d(TAG, "Typing: $text")

        // Focus set karo
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // Pehle existing text clear karo
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }

        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
    }

    /**
     * Text field mein text append karo (existing text ke baad)
     */
    fun appendText(node: AccessibilityNodeInfo, text: String): Boolean {
        val existingText = node.text?.toString() ?: ""
        return typeText(node, existingText + text)
    }

    /**
     * Text field clear karo
     */
    fun clearText(node: AccessibilityNodeInfo): Boolean {
        return typeText(node, "")
    }

    /**
     * Element pe long press karo
     */
    fun longClickNode(node: AccessibilityNodeInfo): Boolean {
        Log.d(TAG, "Long clicking: ${node.text ?: node.contentDescription}")

        if (node.isLongClickable) {
            return node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
        }

        // Gesture-based long press
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return longPressAtCoordinates(rect.centerX().toFloat(), rect.centerY().toFloat())
    }

    /**
     * Scroll forward (neeche)
     */
    fun scrollForward(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    /**
     * Scroll backward (upar)
     */
    fun scrollBackward(node: AccessibilityNodeInfo): Boolean {
        return node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
    }

    // ═══════════════════════════════════════════════════════
    // TEXT-BASED ACTIONS — Text se element dhundo aur click/type
    // ═══════════════════════════════════════════════════════

    /**
     * Text se element dhundo aur click karo
     * @return true agar element mila aur click hua
     */
    fun clickByText(text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = ScreenReader.findElementByText(root, text)

        return if (node != null) {
            clickNode(node)
        } else {
            Log.w(TAG, "Element not found with text: $text")
            false
        }
    }

    /**
     * View ID se element dhundo aur click karo
     */
    fun clickByViewId(viewId: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val node = ScreenReader.findElementByViewId(root, viewId)

        return if (node != null) {
            clickNode(node)
        } else {
            Log.w(TAG, "Element not found with viewId: $viewId")
            false
        }
    }

    /**
     * Pehla editable field dhundo aur text type karo
     */
    fun typeInFirstField(text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val fields = ScreenReader.findEditableFields(root)

        return if (fields.isNotEmpty()) {
            typeText(fields[0], text)
        } else {
            Log.w(TAG, "No editable field found")
            false
        }
    }

    /**
     * Specific text wale field mein type karo
     * @param hint Field ka hint/placeholder text
     * @param text Type karne wala text
     */
    fun typeInFieldWithHint(hint: String, text: String): Boolean {
        val root = service.rootInActiveWindow ?: return false
        val fields = ScreenReader.findEditableFields(root)

        for (field in fields) {
            val fieldHint = field.hintText?.toString() ?: field.text?.toString() ?: ""
            if (fieldHint.contains(hint, ignoreCase = true)) {
                return typeText(field, text)
            }
        }
        Log.w(TAG, "No field found with hint: $hint")
        return false
    }

    // ═══════════════════════════════════════════════════════
    // GESTURE ACTIONS — Tap, Swipe at coordinates
    // ═══════════════════════════════════════════════════════

    /**
     * Get real screen dimensions from service context
     */
    private fun getScreenSize(): Pair<Int, Int> {
        return try {
            val metrics = service.resources.displayMetrics
            Pair(metrics.widthPixels, metrics.heightPixels)
        } catch (e: Exception) {
            Pair(1080, 2400) // Fallback default
        }
    }

    /**
     * Screen pe specific coordinates pe tap karo
     */
    fun tapAtCoordinates(x: Float, y: Float, durationMs: Long = 100): Boolean {
        Log.d(TAG, "Tapping at ($x, $y)")

        val path = Path().apply {
            moveTo(x, y)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        return service.dispatchGesture(gesture, null, null)
    }

    /**
     * Long press at coordinates
     */
    fun longPressAtCoordinates(x: Float, y: Float): Boolean {
        return tapAtCoordinates(x, y, durationMs = 1000)
    }

    /**
     * Swipe gesture — startX,startY se endX,endY tak
     */
    fun swipe(
        startX: Float, startY: Float,
        endX: Float, endY: Float,
        durationMs: Long = 300
    ): Boolean {
        Log.d(TAG, "Swiping from ($startX,$startY) to ($endX,$endY)")

        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()

        return service.dispatchGesture(gesture, null, null)
    }

    /**
     * Screen pe neeche swipe karo (scroll up)
     */
    fun swipeUp(): Boolean {
        val (w, h) = getScreenSize()
        val centerX = w / 2f
        return swipe(centerX, h * 0.7f, centerX, h * 0.3f)
    }

    /**
     * Screen pe upar swipe karo (scroll down)
     */
    fun swipeDown(): Boolean {
        val (w, h) = getScreenSize()
        val centerX = w / 2f
        return swipe(centerX, h * 0.3f, centerX, h * 0.7f)
    }

    /**
     * Left swipe
     */
    fun swipeLeft(): Boolean {
        val (w, h) = getScreenSize()
        val centerY = h / 2f
        return swipe(w * 0.8f, centerY, w * 0.2f, centerY)
    }

    /**
     * Right swipe
     */
    fun swipeRight(): Boolean {
        val (w, h) = getScreenSize()
        val centerY = h / 2f
        return swipe(w * 0.2f, centerY, w * 0.8f, centerY)
    }
}


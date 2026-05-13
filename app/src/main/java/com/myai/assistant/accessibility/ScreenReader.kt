// File: app/src/main/java/com/myai/assistant/accessibility/ScreenReader.kt
// Screen Reader — Screen se text aur UI elements extract karo

package com.myai.assistant.accessibility

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Screen Reader — Screen pe kya hai woh padhta hai
 * AccessibilityNodeInfo tree traverse karke sabhi text collect karta hai
 */
object ScreenReader {

    private const val TAG = "ScreenReader"

    /**
     * Poora screen ka text extract karo
     * Root node se recursively traverse karke sabhi text collect karta hai
     *
     * @param rootNode Root window node
     * @return Full screen text
     */
    fun extractScreenText(rootNode: AccessibilityNodeInfo?): String {
        if (rootNode == null) return ""

        val textParts = mutableListOf<String>()
        traverseAndCollectText(rootNode, textParts)

        return textParts.joinToString("\n").trim()
    }

    /**
     * Screen ka structured snapshot lo
     * Har element ka type, text, clickability sab milega
     *
     * @return List of UI elements with details
     */
    fun getScreenSnapshot(rootNode: AccessibilityNodeInfo?): List<UiElement> {
        if (rootNode == null) return emptyList()

        val elements = mutableListOf<UiElement>()
        traverseAndCollectElements(rootNode, elements, depth = 0)

        return elements
    }

    /**
     * Specific text wala element dhundo
     * (e.g., "Send" button, "Search" field)
     */
    fun findElementByText(
        rootNode: AccessibilityNodeInfo?,
        text: String,
        exactMatch: Boolean = false
    ): AccessibilityNodeInfo? {
        if (rootNode == null) return null

        // Text match check
        val nodeText = rootNode.text?.toString() ?: ""
        val nodeDesc = rootNode.contentDescription?.toString() ?: ""

        val matches = if (exactMatch) {
            nodeText.equals(text, ignoreCase = true) ||
            nodeDesc.equals(text, ignoreCase = true)
        } else {
            nodeText.contains(text, ignoreCase = true) ||
            nodeDesc.contains(text, ignoreCase = true)
        }

        if (matches) return rootNode

        // Children mein dhundo
        for (i in 0 until rootNode.childCount) {
            val child = rootNode.getChild(i) ?: continue
            val found = findElementByText(child, text, exactMatch)
            if (found != null) return found
        }

        return null
    }

    /**
     * Specific view ID wala element dhundo
     * (e.g., "com.whatsapp:id/send")
     */
    fun findElementByViewId(
        rootNode: AccessibilityNodeInfo?,
        viewId: String
    ): AccessibilityNodeInfo? {
        if (rootNode == null) return null

        val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
        return nodes?.firstOrNull()
    }

    /**
     * Clickable elements dhundo
     */
    fun findClickableElements(rootNode: AccessibilityNodeInfo?): List<UiElement> {
        val elements = mutableListOf<UiElement>()
        if (rootNode == null) return elements

        traverseAndCollectClickable(rootNode, elements)
        return elements
    }

    /**
     * Editable text fields dhundo (input fields)
     */
    fun findEditableFields(rootNode: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        val fields = mutableListOf<AccessibilityNodeInfo>()
        if (rootNode == null) return fields

        traverseAndCollectEditable(rootNode, fields)
        return fields
    }

    /**
     * Current app ka package name lo
     */
    fun getCurrentAppPackage(rootNode: AccessibilityNodeInfo?): String {
        return rootNode?.packageName?.toString() ?: "unknown"
    }

    // ═══════════════════════════════════════════
    // PRIVATE HELPERS — Tree traversal
    // ═══════════════════════════════════════════

    private fun traverseAndCollectText(
        node: AccessibilityNodeInfo,
        textParts: MutableList<String>
    ) {
        // Node ka text collect karo
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let {
            textParts.add(it)
        }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let {
            if (!textParts.contains(it)) textParts.add(it)
        }

        // Children traverse karo
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseAndCollectText(child, textParts)
        }
    }

    private fun traverseAndCollectElements(
        node: AccessibilityNodeInfo,
        elements: MutableList<UiElement>,
        depth: Int
    ) {
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""

        // Sirf meaningful elements add karo
        if (text.isNotBlank() || desc.isNotBlank() || node.isClickable || node.isEditable) {
            elements.add(
                UiElement(
                    text = text,
                    contentDescription = desc,
                    viewId = viewId,
                    className = className,
                    isClickable = node.isClickable,
                    isEditable = node.isEditable,
                    isScrollable = node.isScrollable,
                    isChecked = node.isChecked,
                    isEnabled = node.isEnabled,
                    depth = depth,
                    nodeInfo = node
                )
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseAndCollectElements(child, elements, depth + 1)
        }
    }

    private fun traverseAndCollectClickable(
        node: AccessibilityNodeInfo,
        elements: MutableList<UiElement>
    ) {
        if (node.isClickable) {
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            elements.add(
                UiElement(
                    text = text,
                    contentDescription = desc,
                    viewId = node.viewIdResourceName ?: "",
                    className = node.className?.toString() ?: "",
                    isClickable = true,
                    isEditable = node.isEditable,
                    nodeInfo = node
                )
            )
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseAndCollectClickable(child, elements)
        }
    }

    private fun traverseAndCollectEditable(
        node: AccessibilityNodeInfo,
        fields: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.isEditable) {
            fields.add(node)
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseAndCollectEditable(child, fields)
        }
    }
}

/**
 * UI Element — Screen pe ek element ki info
 */
data class UiElement(
    val text: String = "",
    val contentDescription: String = "",
    val viewId: String = "",
    val className: String = "",
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isChecked: Boolean = false,
    val isEnabled: Boolean = true,
    val depth: Int = 0,
    val nodeInfo: AccessibilityNodeInfo? = null
) {
    /** Display text — text ya contentDescription jo bhi available ho */
    val displayText: String get() = text.ifBlank { contentDescription }

    override fun toString(): String {
        val type = className.substringAfterLast(".")
        return "[$type] ${displayText.take(50)} ${if (isClickable) "🔘" else ""} ${if (isEditable) "✏️" else ""}"
    }
}

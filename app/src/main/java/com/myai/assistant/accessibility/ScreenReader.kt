// File: app/src/main/java/com/myai/assistant/accessibility/ScreenReader.kt
// Screen Reader — Screen se text aur UI elements extract karo
// Optimized: Filter token bloat, prioritize interactive elements, extract bounding boxes, iterative stack-safe traversal

package com.myai.assistant.accessibility

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque

/**
 * Screen Reader — Screen pe kya hai woh padhta hai
 * AccessibilityNodeInfo tree traverse karke sabhi text collect karta hai
 */
object ScreenReader {

    private const val TAG = "ScreenReader"
    private const val MAX_TREE_DEPTH = 60

    private data class NodeEntry(
        val node: AccessibilityNodeInfo,
        val depth: Int
    )

    /**
     * Poora screen ka text extract karo
     * Iterative stack-safe traversal karke visible text collect karta hai
     *
     * @param rootNode Root window node
     * @return Full screen text
     */
    fun extractScreenText(rootNode: AccessibilityNodeInfo?): String {
        if (rootNode == null) return ""

        val textParts = mutableListOf<String>()
        val seenText = HashSet<String>()
        val stack = ArrayDeque<NodeEntry>()
        stack.push(NodeEntry(rootNode, 0))

        val tempBounds = Rect()

        while (stack.isNotEmpty()) {
            val entry = stack.pop()
            val node = entry.node
            val depth = entry.depth

            try {
                // Check visibility
                val isVisible = try { node.isVisibleToUser } catch (_: Exception) { true }
                if (!isVisible) {
                    continue
                }

                // Check bounds - filter zero-dimension nodes
                node.getBoundsInScreen(tempBounds)
                val hasDimensions = !tempBounds.isEmpty && tempBounds.width() > 0 && tempBounds.height() > 0

                if (hasDimensions && node.isEnabled) {
                    val nodeText = node.text?.toString()?.trim()
                    if (!nodeText.isNullOrBlank() && seenText.add(nodeText)) {
                        textParts.add(nodeText)
                    }

                    val nodeDesc = node.contentDescription?.toString()?.trim()
                    if (!nodeDesc.isNullOrBlank() && seenText.add(nodeDesc)) {
                        textParts.add(nodeDesc)
                    }
                }

                // Push children in reverse order for pre-order traversal
                if (depth < MAX_TREE_DEPTH) {
                    val childCount = try { node.childCount } catch (_: Exception) { 0 }
                    for (i in childCount - 1 downTo 0) {
                        val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                        stack.push(NodeEntry(child, depth + 1))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error traversing node in extractScreenText: ${e.message}")
            }
        }

        return textParts.joinToString("\n").trim()
    }

    /**
     * Screen ka structured snapshot lo
     * Filters out invisible, disabled, or zero-dimension nodes.
     * Prioritizes interactive elements (buttons, inputs, tabs, actionable icons).
     *
     * @param rootNode Root window node
     * @param prioritizeInteractive If true, sorts actionable interactive elements first
     * @return List of UI elements with precise details and bounding boxes
     */
    fun getScreenSnapshot(
        rootNode: AccessibilityNodeInfo?,
        prioritizeInteractive: Boolean = true
    ): List<UiElement> {
        if (rootNode == null) return emptyList()

        val elements = mutableListOf<UiElement>()
        val stack = ArrayDeque<NodeEntry>()
        stack.push(NodeEntry(rootNode, 0))

        val bounds = Rect()

        while (stack.isNotEmpty()) {
            val entry = stack.pop()
            val node = entry.node
            val depth = entry.depth

            try {
                // 1. Filter out invisible subtrees to eliminate token bloat
                val isVisible = try { node.isVisibleToUser } catch (_: Exception) { true }
                if (!isVisible) {
                    continue
                }

                // 2. Extract bounding box
                node.getBoundsInScreen(bounds)
                val isZeroDimension = bounds.isEmpty || bounds.width() <= 0 || bounds.height() <= 0
                val isEnabled = node.isEnabled

                val text = node.text?.toString()?.trim() ?: ""
                val desc = node.contentDescription?.toString()?.trim() ?: ""
                val viewId = node.viewIdResourceName ?: ""
                val className = node.className?.toString() ?: ""

                val isClickable = node.isClickable
                val isEditable = node.isEditable
                val isScrollable = node.isScrollable
                val isChecked = node.isChecked
                val isCheckable = node.isCheckable

                val hasMeaningfulInfo = text.isNotBlank() || desc.isNotBlank() || isClickable || isEditable

                // 3. Filter out zero-dimension, disabled, or non-meaningful container nodes
                if (hasMeaningfulInfo && !isZeroDimension && isEnabled) {
                    elements.add(
                        UiElement(
                            text = text,
                            contentDescription = desc,
                            viewId = viewId,
                            className = className,
                            isClickable = isClickable,
                            isEditable = isEditable,
                            isScrollable = isScrollable,
                            isChecked = isChecked,
                            isEnabled = isEnabled,
                            depth = depth,
                            bounds = Rect(bounds),
                            nodeInfo = node
                        )
                    )
                }

                // 4. Traverse children up to MAX_TREE_DEPTH
                if (depth < MAX_TREE_DEPTH) {
                    val childCount = try { node.childCount } catch (_: Exception) { 0 }
                    for (i in childCount - 1 downTo 0) {
                        val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                        stack.push(NodeEntry(child, depth + 1))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in getScreenSnapshot traversal: ${e.message}")
            }
        }

        return if (prioritizeInteractive) {
            elements.sortedWith(
                compareBy(
                    { getInteractivePriority(it) },
                    { it.depth }
                )
            )
        } else {
            elements
        }
    }

    /**
     * Compute priority rank for UI elements (lower = higher priority):
     * 1. Clickable buttons, tabs, chips
     * 2. Editable text fields (inputs)
     * 3. Clickable icons / items with content description
     * 4. Other clickable elements
     * 5. Checkable/scrollable controls
     * 6. Elements with text
     * 7. Other elements
     */
    private fun getInteractivePriority(element: UiElement): Int {
        val className = element.className
        return when {
            element.isClickable && (className.contains("Button", ignoreCase = true) || className.contains("Tab", ignoreCase = true) || className.contains("Chip", ignoreCase = true)) -> 1
            element.isEditable || className.contains("EditText", ignoreCase = true) -> 2
            element.isClickable && element.contentDescription.isNotBlank() -> 3
            element.isClickable -> 4
            element.isChecked || element.isScrollable -> 5
            element.text.isNotBlank() -> 6
            else -> 7
        }
    }

    /**
     * Specific text wala element dhundo
     * (e.g., "Send" button, "Search" field)
     * Prioritizes clickable and visible elements matching the text.
     */
    fun findElementByText(
        rootNode: AccessibilityNodeInfo?,
        text: String,
        exactMatch: Boolean = false
    ): AccessibilityNodeInfo? {
        if (rootNode == null || text.isBlank()) return null

        val stack = ArrayDeque<NodeEntry>()
        stack.push(NodeEntry(rootNode, 0))

        var bestMatch: AccessibilityNodeInfo? = null
        var bestScore = -1 // Higher is better

        val rect = Rect()

        while (stack.isNotEmpty()) {
            val entry = stack.pop()
            val node = entry.node
            val depth = entry.depth

            try {
                val isVisible = try { node.isVisibleToUser } catch (_: Exception) { true }
                if (!isVisible) continue

                val nodeText = node.text?.toString() ?: ""
                val nodeDesc = node.contentDescription?.toString() ?: ""

                val isExactText = nodeText.equals(text, ignoreCase = true) || nodeDesc.equals(text, ignoreCase = true)
                val isPartialText = nodeText.contains(text, ignoreCase = true) || nodeDesc.contains(text, ignoreCase = true)

                val matches = if (exactMatch) isExactText else isPartialText

                if (matches) {
                    node.getBoundsInScreen(rect)
                    val hasValidBounds = !rect.isEmpty && rect.width() > 0 && rect.height() > 0

                    // Calculate suitability score
                    var score = 0
                    if (isExactText) score += 100
                    if (node.isClickable) score += 50
                    if (node.isEditable) score += 40
                    if (node.isEnabled) score += 20
                    if (hasValidBounds) score += 20

                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = node
                        // If we found a perfect clickable exact match with valid bounds, return immediately
                        if (isExactText && node.isClickable && hasValidBounds) {
                            return node
                        }
                    }
                }

                if (depth < MAX_TREE_DEPTH) {
                    val childCount = try { node.childCount } catch (_: Exception) { 0 }
                    for (i in childCount - 1 downTo 0) {
                        val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                        stack.push(NodeEntry(child, depth + 1))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in findElementByText traversal: ${e.message}")
            }
        }

        return bestMatch
    }

    /**
     * Extract precise screen bounding box for coordinate clicks if text-based click fails.
     *
     * @param rootNode Root window node
     * @param text Target text to match
     * @param exactMatch Whether exact string match is required
     * @return Bounding rectangle or null if element not found or has zero dimensions
     */
    fun findElementBoundsByText(
        rootNode: AccessibilityNodeInfo?,
        text: String,
        exactMatch: Boolean = false
    ): Rect? {
        val node = findElementByText(rootNode, text, exactMatch) ?: return null
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return if (!rect.isEmpty && rect.width() > 0 && rect.height() > 0) rect else null
    }

    /**
     * Specific view ID wala element dhundo
     * (e.g., "com.whatsapp:id/send")
     */
    fun findElementByViewId(
        rootNode: AccessibilityNodeInfo?,
        viewId: String
    ): AccessibilityNodeInfo? {
        if (rootNode == null || viewId.isBlank()) return null

        // Try fast native lookup first
        try {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(viewId)
            if (!nodes.isNullOrEmpty()) {
                val clickable = nodes.firstOrNull { it.isClickable && it.isVisibleToUser }
                return clickable ?: nodes.first()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Native findAccessibilityNodeInfosByViewId failed: ${e.message}")
        }

        // Fallback: Iterative stack-safe search (supports partial view ID match like ":id/send")
        val stack = ArrayDeque<NodeEntry>()
        stack.push(NodeEntry(rootNode, 0))

        while (stack.isNotEmpty()) {
            val entry = stack.pop()
            val node = entry.node
            val depth = entry.depth

            try {
                val nodeViewId = node.viewIdResourceName
                if (nodeViewId != null && nodeViewId.contains(viewId, ignoreCase = true)) {
                    return node
                }

                if (depth < MAX_TREE_DEPTH) {
                    val childCount = try { node.childCount } catch (_: Exception) { 0 }
                    for (i in childCount - 1 downTo 0) {
                        val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                        stack.push(NodeEntry(child, depth + 1))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in findElementByViewId fallback: ${e.message}")
            }
        }

        return null
    }

    /**
     * Clickable elements dhundo
     */
    fun findClickableElements(rootNode: AccessibilityNodeInfo?): List<UiElement> {
        if (rootNode == null) return emptyList()

        val elements = mutableListOf<UiElement>()
        val stack = ArrayDeque<NodeEntry>()
        stack.push(NodeEntry(rootNode, 0))
        val bounds = Rect()

        while (stack.isNotEmpty()) {
            val entry = stack.pop()
            val node = entry.node
            val depth = entry.depth

            try {
                val isVisible = try { node.isVisibleToUser } catch (_: Exception) { true }
                if (!isVisible) continue

                node.getBoundsInScreen(bounds)
                val isZeroDimension = bounds.isEmpty || bounds.width() <= 0 || bounds.height() <= 0

                if (node.isClickable && node.isEnabled && !isZeroDimension) {
                    val text = node.text?.toString()?.trim() ?: ""
                    val desc = node.contentDescription?.toString()?.trim() ?: ""
                    elements.add(
                        UiElement(
                            text = text,
                            contentDescription = desc,
                            viewId = node.viewIdResourceName ?: "",
                            className = node.className?.toString() ?: "",
                            isClickable = true,
                            isEditable = node.isEditable,
                            isScrollable = node.isScrollable,
                            isChecked = node.isChecked,
                            isEnabled = node.isEnabled,
                            depth = depth,
                            bounds = Rect(bounds),
                            nodeInfo = node
                        )
                    )
                }

                if (depth < MAX_TREE_DEPTH) {
                    val childCount = try { node.childCount } catch (_: Exception) { 0 }
                    for (i in childCount - 1 downTo 0) {
                        val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                        stack.push(NodeEntry(child, depth + 1))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in findClickableElements traversal: ${e.message}")
            }
        }

        return elements
    }

    /**
     * Editable text fields dhundo (input fields)
     */
    fun findEditableFields(rootNode: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
        if (rootNode == null) return emptyList()

        val fields = mutableListOf<AccessibilityNodeInfo>()
        val stack = ArrayDeque<NodeEntry>()
        stack.push(NodeEntry(rootNode, 0))

        while (stack.isNotEmpty()) {
            val entry = stack.pop()
            val node = entry.node
            val depth = entry.depth

            try {
                val isVisible = try { node.isVisibleToUser } catch (_: Exception) { true }
                if (!isVisible) continue

                if (node.isEditable && node.isEnabled) {
                    fields.add(node)
                }

                if (depth < MAX_TREE_DEPTH) {
                    val childCount = try { node.childCount } catch (_: Exception) { 0 }
                    for (i in childCount - 1 downTo 0) {
                        val child = try { node.getChild(i) } catch (_: Exception) { null } ?: continue
                        stack.push(NodeEntry(child, depth + 1))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error in findEditableFields traversal: ${e.message}")
            }
        }

        return fields
    }

    /**
     * Current app ka package name lo
     */
    fun getCurrentAppPackage(rootNode: AccessibilityNodeInfo?): String {
        return rootNode?.packageName?.toString() ?: "unknown"
    }
}

/**
 * UI Element — Screen pe ek element ki info with precise coordinates
 */
data class UiElement(
    val text: String = "",
    val contentDescription: String = "",
    val viewId: String = "",
    val className: String = "",
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val handyFocus: Boolean = false,
    val isChecked: Boolean = false,
    val isEnabled: Boolean = true,
    val depth: Int = 0,
    val bounds: Rect = Rect(),
    val nodeInfo: AccessibilityNodeInfo? = null
) {
    /** Display text — text ya contentDescription jo bhi available ho */
    val displayText: String get() = text.ifBlank { contentDescription }

    /** Bounding box center coordinates for coordinate clicks */
    val centerX: Int get() = bounds.centerX()
    val centerY: Int get() = bounds.centerY()
    val width: Int get() = bounds.width()
    val height: Int get() = bounds.height()

    /** Is this element actionable/interactive? */
    val isInteractive: Boolean
        get() = isClickable || isEditable || isScrollable || isChecked ||
                className.contains("Button", ignoreCase = true) ||
                className.contains("EditText", ignoreCase = true) ||
                className.contains("Tab", ignoreCase = true)

    val isZeroDimension: Boolean
        get() = bounds.isEmpty || bounds.width() <= 0 || bounds.height() <= 0

    override fun toString(): String {
        val type = className.substringAfterLast(".")
        val clickable = if (isClickable) "🔘" else ""
        val editable = if (isEditable) "✏️" else ""
        val boundsStr = if (!bounds.isEmpty) "(${bounds.centerX()},${bounds.centerY()})" else ""
        return "[$type] ${displayText.take(50)} $clickable $editable $boundsStr".trim()
    }
}

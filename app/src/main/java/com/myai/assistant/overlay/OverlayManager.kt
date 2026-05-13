// File: app/src/main/java/com/myai/assistant/overlay/OverlayManager.kt
// Overlay Manager — Floating bubble UI create/manage (XML-based, no Compose)

package com.myai.assistant.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

/**
 * Overlay Manager — Floating AI bubble dikhao/hatao
 * Traditional View-based (ComposeView service mein crash karta hai bina LifecycleOwner ke)
 * Drag support, tap to expand
 */
class OverlayManager(private val context: Context) {

    private var windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var bubbleContainer: FrameLayout? = null
    private var isShowing = false

    var onBubbleClick: (() -> Unit)? = null

    /**
     * Floating bubble dikhao
     */
    fun showBubble() {
        if (isShowing) return

        val params = WindowManager.LayoutParams(
            dpToPx(56),
            dpToPx(56),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        // Bubble view banao (traditional Android Views)
        val container = FrameLayout(context).apply {
            // Gradient circular background
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(
                    Color.parseColor("#3B82F6"),  // Blue
                    Color.parseColor("#8B5CF6"),  // Purple
                    Color.parseColor("#06B6D4")   // Cyan
                )
            ).apply {
                shape = GradientDrawable.OVAL
                setSize(dpToPx(56), dpToPx(56))
            }
            background = gradient
            elevation = 12f
        }

        // AI icon (using built-in star icon)
        val iconView = ImageView(context).apply {
            setImageResource(android.R.drawable.btn_star_big_on)
            setColorFilter(Color.WHITE)
            val padding = dpToPx(14)
            setPadding(padding, padding, padding, padding)
        }
        container.addView(iconView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ))

        // Drag support
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var isDragging = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (abs(dx) > 5 || abs(dy) > 5) isDragging = true
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    try { windowManager.updateViewLayout(container, params) } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) onBubbleClick?.invoke()
                    true
                }
                else -> false
            }
        }

        bubbleContainer = container

        try {
            windowManager.addView(container, params)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Bubble hatao
     */
    fun hideBubble() {
        if (!isShowing) return
        try {
            bubbleContainer?.let { windowManager.removeView(it) }
        } catch (_: Exception) {}
        bubbleContainer = null
        isShowing = false
    }

    fun isVisible() = isShowing

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

// File: app/src/main/java/com/myai/assistant/overlay/OverlayManager.kt
// Overlay Manager — Floating bubble UI create/manage (XML/View-based)

package com.myai.assistant.overlay

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.*
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.hypot

/**
 * Overlay Manager — Floating AI bubble WindowManager implementation.
 * Features:
 * - Proper WindowManager params (TYPE_APPLICATION_OVERLAY, FLAG_NOT_FOCUSABLE, FLAG_LAYOUT_NO_LIMITS, TRANSLUCENT)
 * - Touch slop-based drag threshold preventing click/drag conflicts
 * - Immediate long-press detection with haptic feedback
 * - Magnetic edge snapping on touch release
 * - Fluid state animations (Idle, Listening, Thinking, Speaking, Success, Error)
 */
class OverlayManager(private val context: Context) {

    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var bubbleContainer: FrameLayout? = null
    private var iconView: ImageView? = null
    private var windowLayoutParams: WindowManager.LayoutParams? = null
    private var isShowing = false

    // State Animators
    private var pulseAnimator: ValueAnimator? = null
    private var rotationAnimator: ObjectAnimator? = null
    private var edgeSnapAnimator: ValueAnimator? = null
    private var longPressRunnable: Runnable? = null

    enum class BubbleState {
        IDLE, LISTENING, THINKING, SPEAKING, SUCCESS, ERROR
    }

    var onBubbleClick: (() -> Unit)? = null
    var onBubbleLongClick: (() -> Unit)? = null

    var currentState = BubbleState.IDLE
        private set

    /**
     * Floating bubble state update (colors, glow, and animations)
     */
    fun setBubbleState(state: BubbleState) {
        currentState = state
        val container = bubbleContainer ?: return

        val colors = when (state) {
            BubbleState.IDLE -> intArrayOf(
                Color.parseColor("#0055FF"),
                Color.parseColor("#8B5CF6"),
                Color.parseColor("#06B6D4")
            )
            BubbleState.LISTENING -> intArrayOf(
                Color.parseColor("#00F0FF"),
                Color.parseColor("#0088FF"),
                Color.parseColor("#00E5FF")
            )
            BubbleState.THINKING -> intArrayOf(
                Color.parseColor("#D000FF"),
                Color.parseColor("#8B5CF6"),
                Color.parseColor("#EC4899")
            )
            BubbleState.SPEAKING -> intArrayOf(
                Color.parseColor("#00FF88"),
                Color.parseColor("#10B981"),
                Color.parseColor("#06B6D4")
            )
            BubbleState.SUCCESS -> intArrayOf(
                Color.parseColor("#00FF88"),
                Color.parseColor("#10B981"),
                Color.parseColor("#34D399")
            )
            BubbleState.ERROR -> intArrayOf(
                Color.parseColor("#FF3B30"),
                Color.parseColor("#EF4444"),
                Color.parseColor("#F87171")
            )
        }

        container.post {
            // Update Background Gradient
            val gradient = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
                shape = GradientDrawable.OVAL
                setSize(dpToPx(60), dpToPx(60))
            }
            container.background = gradient

            // Update Icon
            iconView?.let { iv ->
                when (state) {
                    BubbleState.IDLE, BubbleState.SPEAKING -> {
                        iv.setImageResource(android.R.drawable.ic_btn_speak_now)
                        iv.setColorFilter(Color.WHITE)
                    }
                    BubbleState.LISTENING -> {
                        iv.setImageResource(android.R.drawable.ic_btn_speak_now)
                        iv.setColorFilter(Color.WHITE)
                    }
                    BubbleState.THINKING -> {
                        iv.setImageResource(android.R.drawable.ic_popup_sync)
                        iv.setColorFilter(Color.WHITE)
                    }
                    BubbleState.SUCCESS -> {
                        iv.setImageResource(android.R.drawable.checkbox_on_background)
                        iv.setColorFilter(Color.WHITE)
                    }
                    BubbleState.ERROR -> {
                        iv.setImageResource(android.R.drawable.stat_notify_error)
                        iv.setColorFilter(Color.WHITE)
                    }
                }
            }

            // Stop previous looping animations
            stopStateAnimations()

            // State-specific dynamic animations
            when (state) {
                BubbleState.IDLE -> {
                    // Gentle breathing pulse
                    startPulseAnimation(container, 0.96f, 1.04f, 1500)
                }
                BubbleState.LISTENING -> {
                    // Fast active listening pulse
                    startPulseAnimation(container, 1.0f, 1.18f, 500)
                }
                BubbleState.THINKING -> {
                    // Continuous rotation storm
                    rotationAnimator = ObjectAnimator.ofFloat(container, "rotation", 0f, 360f).apply {
                        duration = 1400
                        repeatCount = ValueAnimator.INFINITE
                        interpolator = AccelerateDecelerateInterpolator()
                        start()
                    }
                    startPulseAnimation(container, 1.05f, 1.14f, 600)
                }
                BubbleState.SPEAKING -> {
                    // Audio waveform pulse
                    startPulseAnimation(container, 0.98f, 1.12f, 400)
                }
                BubbleState.SUCCESS -> {
                    // Quick celebratory scale burst
                    container.animate()
                        .scaleX(1.25f)
                        .scaleY(1.25f)
                        .setDuration(200)
                        .withEndAction {
                            container.animate().scaleX(1.0f).scaleY(1.0f).setDuration(200).start()
                        }
                        .start()
                }
                BubbleState.ERROR -> {
                    // Horizontal shudder / shake
                    val shakeDistance = dpToPx(8).toFloat()
                    val originalX = container.translationX
                    val shake = ObjectAnimator.ofFloat(
                        container,
                        "translationX",
                        originalX,
                        originalX - shakeDistance,
                        originalX + shakeDistance,
                        originalX - shakeDistance,
                        originalX + shakeDistance,
                        originalX
                    ).apply {
                        duration = 350
                        start()
                    }
                }
            }
        }
    }

    private fun startPulseAnimation(view: View, minScale: Float, maxScale: Float, durationMs: Long) {
        pulseAnimator = ValueAnimator.ofFloat(minScale, maxScale).apply {
            duration = durationMs
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { va ->
                val scale = va.animatedValue as Float
                view.scaleX = scale
                view.scaleY = scale
            }
            start()
        }
    }

    private fun stopStateAnimations() {
        pulseAnimator?.cancel()
        pulseAnimator = null

        rotationAnimator?.cancel()
        rotationAnimator = null

        bubbleContainer?.let {
            it.rotation = 0f
            it.translationX = 0f
            it.scaleX = 1.0f
            it.scaleY = 1.0f
        }
    }

    /**
     * Floating bubble dikhao (WindowManager integration)
     */
    fun showBubble() {
        if (isShowing && bubbleContainer?.isAttachedToWindow == true) return
        hideBubble()

        val bubbleSize = dpToPx(60)
        val params = WindowManager.LayoutParams(
            bubbleSize,
            bubbleSize,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 350
        }
        windowLayoutParams = params

        // Bubble root container
        val container = FrameLayout(context).apply {
            val gradient = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#0055FF"), Color.parseColor("#8B5CF6"), Color.parseColor("#06B6D4"))
            ).apply {
                shape = GradientDrawable.OVAL
                setSize(bubbleSize, bubbleSize)
            }
            background = gradient
            elevation = 16f
        }

        // AI Icon
        val iv = ImageView(context).apply {
            setImageResource(android.R.drawable.ic_btn_speak_now)
            setColorFilter(Color.WHITE)
            val padding = dpToPx(14)
            setPadding(padding, padding, padding, padding)
        }
        container.addView(iv, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ))
        iconView = iv

        // Drag & Touch Gestures
        var initialParamsX = 0
        var initialParamsY = 0
        var touchDownX = 0f
        var touchDownY = 0f
        var isDragging = false
        var hasTriggeredLongPress = false

        container.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialParamsX = params.x
                    initialParamsY = params.y
                    touchDownX = event.rawX
                    touchDownY = event.rawY
                    isDragging = false
                    hasTriggeredLongPress = false

                    // Tactile down scale
                    v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(120).start()

                    // Long press detection
                    longPressRunnable = Runnable {
                        if (!isDragging) {
                            hasTriggeredLongPress = true
                            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            v.animate().scaleX(1.12f).scaleY(1.12f).setDuration(150).withEndAction {
                                v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start()
                            }.start()
                            onBubbleLongClick?.invoke()
                        }
                    }
                    handler.postDelayed(longPressRunnable!!, ViewConfiguration.getLongPressTimeout().toLong())
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchDownX
                    val dy = event.rawY - touchDownY

                    if (!isDragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                        // Cancel pending long press when drag starts
                        longPressRunnable?.let { handler.removeCallbacks(it) }
                    }

                    if (isDragging) {
                        val newX = (initialParamsX + dx).toInt()
                        val newY = (initialParamsY + dy).toInt()
                        if (kotlin.math.abs(newX - params.x) > 2 || kotlin.math.abs(newY - params.y) > 2) {
                            params.x = newX
                            params.y = newY
                            try {
                                if (container.isAttachedToWindow) {
                                    windowManager.updateViewLayout(container, params)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { handler.removeCallbacks(it) }

                    if (!isDragging && !hasTriggeredLongPress) {
                        // Clean Tap
                        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                        onBubbleClick?.invoke()
                    } else if (isDragging) {
                        // Drag completed: Smooth Magnetic Edge Snapping
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                        snapToEdge(container, params)
                    } else {
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                    }
                    true
                }

                else -> false
            }
        }

        bubbleContainer = container

        try {
            windowManager.addView(container, params)
            isShowing = true
            setBubbleState(BubbleState.IDLE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Smooth magnetic snap to left or right screen edge
     */
    private fun snapToEdge(container: View, params: WindowManager.LayoutParams) {
        edgeSnapAnimator?.cancel()

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val bubbleSize = dpToPx(60)
        val margin = dpToPx(8)

        val targetX = if (params.x + bubbleSize / 2 < screenWidth / 2) {
            margin
        } else {
            screenWidth - bubbleSize - margin
        }

        edgeSnapAnimator = ValueAnimator.ofInt(params.x, targetX).apply {
            duration = 260
            interpolator = DecelerateInterpolator()
            addUpdateListener { va ->
                params.x = va.animatedValue as Int
                try {
                    windowManager.updateViewLayout(container, params)
                } catch (_: Exception) {}
            }
            start()
        }
    }

    /**
     * Floating bubble dismiss/cleanup
     */
    fun hideBubble() {
        if (!isShowing) return
        longPressRunnable?.let { handler.removeCallbacks(it) }
        stopStateAnimations()
        edgeSnapAnimator?.cancel()

        try {
            bubbleContainer?.let {
                if (it.isAttachedToWindow) {
                    windowManager.removeView(it)
                }
            }
        } catch (_: Exception) {}

        bubbleContainer = null
        iconView = null
        windowLayoutParams = null
        isShowing = false
    }

    fun isVisible() = isShowing

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}

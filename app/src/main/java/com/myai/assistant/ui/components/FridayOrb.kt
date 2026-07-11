// File: app/src/main/java/com/myai/assistant/ui/components/FridayOrb.kt
package com.myai.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.myai.assistant.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🤖 FridayOrb — High-performance Sci-Fi Holographic AI Orb
 * Animates, breathes, rotates, and changes colors based on assistant status.
 */
@Composable
fun FridayOrb(
    isListening: Boolean,
    isAiThinking: Boolean,
    isSpeaking: Boolean,
    lastAction: String?,
    modifier: Modifier = Modifier
) {
    // 1. Color State mapping based on AI action or listening status
    val baseColor = when {
        isListening -> Color(0xFF00F0FF) // Neon Cyan
        isAiThinking -> Color(0xFFD000FF) // Glowing Magenta/Purple
        isSpeaking -> Color(0xFF00FF88) // Vivid Neon Green
        lastAction != null -> {
            when (lastAction.uppercase()) {
                "CALL", "SMS" -> Color(0xFFFF3B30) // Red
                "WHATSAPP_MSG" -> Color(0xFF25D366) // WhatsApp Green
                "YOUTUBE_SEARCH" -> Color(0xFF00A2FF) // YouTube Blue
                "SET_ALARM", "SET_TIMER" -> Color(0xFFFF9500) // Orange
                "LOCATION" -> Color(0xFF00FFFF) // Cyan
                else -> Color(0xFF3B82F6) // Electric Blue (Default Action)
            }
        }
        else -> Color(0xFF0055FF) // Electric deep blue (Idle)
    }

    val glowColor = baseColor.copy(alpha = 0.5f)
    val animatedColor by animateColorAsState(
        targetValue = baseColor,
        animationSpec = tween(durationMillis = 600, easing = LinearOutSlowInEasing),
        label = "color"
    )

    // 2. Infinite transitions for breathing & rotations
    val infiniteTransition = rememberInfiniteTransition(label = "orb_rotation")

    // Idle breathing pulse (slow sinus scaling)
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = SineToSineEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // Fast pulse when listening or speaking
    val activeScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "active_pulse"
    )

    val currentScale = if (isListening || isSpeaking || isAiThinking) activeScale else breatheScale

    // Rotating HUD rings angles
    val ring1Rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAiThinking) 2500 else 8000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring1"
    )

    val ring2Rotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAiThinking) 1800 else 6000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2"
    )

    // Plasma wave oscillations
    val waveOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )

    val waveOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    // 3. Particle System (Static list of offsets/speeds)
    val particles = remember {
        List(24) {
            Particle(
                angle = (Math.random() * 2 * Math.PI).toFloat(),
                radiusFraction = (0.2f + Math.random() * 0.8f).toFloat(),
                speed = (0.01f + Math.random() * 0.02f).toFloat(),
                size = (3..8).random().toFloat()
            )
        }
    }

    // Canvas drawing
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerX = width / 2f
        val centerY = height / 2f
        val baseRadius = size.minDimension * 0.35f * currentScale

        // A. Background Glow aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(animatedColor.copy(alpha = 0.35f), Color.Transparent),
                center = Offset(centerX, centerY),
                radius = baseRadius * 1.8f
            ),
            radius = baseRadius * 1.8f,
            center = Offset(centerX, centerY)
        )

        // B. Concentric HUD rings
        // Ring 1 (Dashed Outer Ring - rotates clockwise)
        rotate(degrees = ring1Rotation, pivot = Offset(centerX, centerY)) {
            drawCircle(
                color = animatedColor.copy(alpha = 0.4f),
                radius = baseRadius * 1.25f,
                center = Offset(centerX, centerY),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 35f), 0f)
                )
            )

            // Inner Ring 1 tick marks
            drawCircle(
                color = animatedColor.copy(alpha = 0.3f),
                radius = baseRadius * 1.1f,
                center = Offset(centerX, centerY),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 25f), 0f)
                )
            )
        }

        // Ring 2 (Dotted Inner Ring - rotates counter-clockwise)
        rotate(degrees = ring2Rotation, pivot = Offset(centerX, centerY)) {
            drawCircle(
                color = animatedColor.copy(alpha = 0.5f),
                radius = baseRadius * 1.2f,
                center = Offset(centerX, centerY),
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 60f), 0f)
                )
            )

            // Decorative sci-fi arcs
            drawArc(
                color = animatedColor.copy(alpha = 0.6f),
                startAngle = 45f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(baseRadius * 2.4f, baseRadius * 2.4f),
                topLeft = Offset(centerX - baseRadius * 1.2f, centerY - baseRadius * 1.2f)
            )
            drawArc(
                color = animatedColor.copy(alpha = 0.6f),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(baseRadius * 2.4f, baseRadius * 2.4f),
                topLeft = Offset(centerX - baseRadius * 1.2f, centerY - baseRadius * 1.2f)
            )
        }

        // C. Central Plasma Wave Spheres (Layered paths for fluid movement)
        val plasmaPath1 = Path()
        val plasmaPath2 = Path()

        val steps = 60
        for (i in 0..steps) {
            val angle = (i * 2 * Math.PI / steps).toFloat()
            // Wave offsets deform the circle radius dynamically
            val offset1 = sin(angle * 5 + waveOffset1) * (baseRadius * 0.08f)
            val offset2 = cos(angle * 4 + waveOffset2) * (baseRadius * 0.06f)

            val r1 = baseRadius * 0.85f + offset1
            val r2 = baseRadius * 0.75f + offset2

            val x1 = centerX + r1 * cos(angle)
            val y1 = centerY + r1 * sin(angle)
            val x2 = centerX + r2 * cos(angle)
            val y2 = centerY + r2 * sin(angle)

            if (i == 0) {
                plasmaPath1.moveTo(x1, y1)
                plasmaPath2.moveTo(x2, y2)
            } else {
                plasmaPath1.lineTo(x1, y1)
                plasmaPath2.lineTo(x2, y2)
            }
        }
        plasmaPath1.close()
        plasmaPath2.close()

        // Draw layered plasma paths with gradients
        drawPath(
            path = plasmaPath1,
            brush = Brush.radialGradient(
                colors = listOf(animatedColor.copy(alpha = 0.4f), animatedColor.copy(alpha = 0.05f)),
                center = Offset(centerX, centerY),
                radius = baseRadius
            )
        )
        drawPath(
            path = plasmaPath1,
            color = animatedColor.copy(alpha = 0.8f),
            style = Stroke(width = 2.dp.toPx())
        )

        drawPath(
            path = plasmaPath2,
            brush = Brush.radialGradient(
                colors = listOf(animatedColor.copy(alpha = 0.6f), Color.Transparent),
                center = Offset(centerX, centerY),
                radius = baseRadius * 0.8f
            )
        )
        drawPath(
            path = plasmaPath2,
            color = animatedColor,
            style = Stroke(width = 1.dp.toPx())
        )

        // D. Core Bright Sphere (Source of energy)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, animatedColor.copy(alpha = 0.8f), Color.Transparent),
                center = Offset(centerX, centerY),
                radius = baseRadius * 0.5f
            ),
            radius = baseRadius * 0.5f,
            center = Offset(centerX, centerY)
        )

        // E. Floating Sci-Fi Particles
        particles.forEach { p ->
            // Slowly rotate particles around center
            val speedFactor = if (isAiThinking) 4f else if (isListening || isSpeaking) 2f else 1f
            p.angle += p.speed * speedFactor

            val pRadius = baseRadius * p.radiusFraction
            val px = centerX + pRadius * cos(p.angle)
            val py = centerY + pRadius * sin(p.angle)

            // Draw glowing particle
            drawCircle(
                color = animatedColor.copy(alpha = 0.7f),
                radius = p.size / 2f,
                center = Offset(px, py)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(animatedColor.copy(alpha = 0.4f), Color.Transparent),
                    center = Offset(px, py),
                    radius = p.size * 2f
                ),
                radius = p.size * 2f,
                center = Offset(px, py)
            )
        }
    }
}

// Particle helper class
private class Particle(
    var angle: Float,
    val radiusFraction: Float,
    val speed: Float,
    val size: Float
)

// Linear custom interpolator for breathing animation
private val SineToSineEasing = Easing { fraction ->
    sin(fraction * Math.PI).toFloat()
}

// File: app/src/main/java/com/myai/assistant/ui/components/FridayOrb.kt
package com.myai.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🤖 FridayOrb — High-performance Sci-Fi Holographic AI Orb
 * Zero-allocation drawScope pipeline optimized for fluid 60fps / 120fps rendering.
 * Supports 6 distinct animated states:
 * 1. Idle breathe
 * 2. Listening pulse & oscilloscope waves
 * 3. Thinking rotating storm & inward vortex
 * 4. Speaking waveform & acoustic ripples
 * 5. Success burst shockwave
 * 6. Error shake & crimson warning strobe
 */
@Composable
fun FridayOrb(
    isListening: Boolean,
    isAiThinking: Boolean,
    isSpeaking: Boolean,
    lastAction: String?,
    isError: Boolean = false,
    isSuccess: Boolean = false,
    modifier: Modifier = Modifier
) {
    // 1. Color State mapping based on AI action, error, success or status
    val baseColor = when {
        isError -> Color(0xFFFF3B30) // Crimson Red (Error)
        isSuccess -> Color(0xFF00FF88) // Vivid Emerald Green (Task Done)
        isListening -> Color(0xFF00F0FF) // Neon Cyan
        isAiThinking -> Color(0xFFD000FF) // Glowing Magenta/Purple
        isSpeaking -> Color(0xFF00FF88) // Vivid Neon Green
        lastAction != null -> {
            when (lastAction.uppercase()) {
                "CALL", "SMS" -> Color(0xFFFF5500) // Red-Orange (Calling)
                "WHATSAPP_MSG" -> Color(0xFF25D366) // WhatsApp Green
                "YOUTUBE_SEARCH" -> Color(0xFFFF0000) // YouTube Red
                "SET_ALARM", "SET_TIMER" -> Color(0xFFFF9500) // Orange (Alarm)
                "LOCATION" -> Color(0xFF0055FF) // Location Blue
                "FLASHLIGHT" -> Color(0xFFFFD600) // Flashlight Yellow
                "PLAY_MUSIC" -> Color(0xFFFF2D55) // Music Pink
                else -> Color(0xFF3B82F6) // Electric Blue (Default Action)
            }
        }
        else -> Color(0xFF0055FF) // Electric deep blue (Idle)
    }

    val animatedColor by animateColorAsState(
        targetValue = baseColor,
        animationSpec = tween(durationMillis = 500, easing = LinearOutSlowInEasing),
        label = "orb_color"
    )

    // Pre-allocate Brush color lists keyed on animatedColor (zero per-frame allocations)
    val glowColors = remember(animatedColor) {
        listOf(animatedColor.copy(alpha = 0.35f), Color.Transparent)
    }
    val plasma1Colors = remember(animatedColor) {
        listOf(animatedColor.copy(alpha = 0.45f), animatedColor.copy(alpha = 0.05f))
    }
    val plasma2Colors = remember(animatedColor) {
        listOf(animatedColor.copy(alpha = 0.65f), Color.Transparent)
    }
    val coreColors = remember(animatedColor) {
        listOf(Color.White, animatedColor.copy(alpha = 0.85f), Color.Transparent)
    }
    val waveColors = remember(animatedColor) {
        listOf(
            animatedColor.copy(alpha = 0.65f),
            animatedColor.copy(alpha = 0.40f),
            animatedColor.copy(alpha = 0.20f)
        )
    }

    // Cached PathEffect objects to avoid continuous floatArrayOf and native allocations
    val dashEffectRing1 = remember {
        PathEffect.dashPathEffect(floatArrayOf(15f, 35f), 0f)
    }
    val dashEffectRing1Inner = remember {
        PathEffect.dashPathEffect(floatArrayOf(5f, 25f), 0f)
    }
    val dashEffectRing2 = remember {
        PathEffect.dashPathEffect(floatArrayOf(40f, 60f), 0f)
    }
    val dashEffectOrbit = remember {
        PathEffect.dashPathEffect(floatArrayOf(10f, 15f), 0f)
    }

    // Pre-allocated reusable Paths (cleared and reused each frame)
    val plasmaPath1 = remember { Path() }
    val plasmaPath2 = remember { Path() }
    val wavePaths = remember { List(3) { Path() } }

    // Wave parameters (constants)
    val waveFrequencies = remember { floatArrayOf(0.015f, 0.025f, 0.035f) }
    val waveSpeeds = remember { floatArrayOf(0.1f, 0.07f, 0.13f) }

    // 2. Infinite transitions for breathing, rotations, and waves
    val infiniteTransition = rememberInfiniteTransition(label = "orb_animations")

    // Idle breathing pulse (slow sinus scaling)
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = SineToSineEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // Fast pulse when listening, thinking, or speaking
    val activeScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAiThinking) 400 else 650,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "active_pulse"
    )

    val currentScale = if (isListening || isSpeaking || isAiThinking) activeScale else breatheScale

    // Rotating HUD rings angles (accelerate sharply when thinking)
    val ring1Rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAiThinking) 1600 else 8000,
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
                durationMillis = if (isAiThinking) 1200 else 6000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring2"
    )

    val ring3Rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAiThinking) 2400 else 12000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring3"
    )

    val ring4Rotation by infiniteTransition.animateFloat(
        initialValue = 360f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAiThinking) 2000 else 10000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "ring4"
    )

    // Plasma wave oscillations
    val waveOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAiThinking) 900 else 2000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave1"
    )

    val waveOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAiThinking) 750 else 1500,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave2"
    )

    // Continuous particle master rotation (drives particle positions deterministically)
    val particleSpin by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isAiThinking) 2500 else 10000,
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "particle_spin"
    )

    // Orbiting angle (for Calling mode)
    val orbitAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phone_orbit"
    )

    // Pulse animation (for Play button in YouTube mode)
    val playPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.18f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "play_pulse"
    )

    // Spinning clock hands animation
    val clockHandRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "clock_hand"
    )

    // Bouncing fraction (for Location mode)
    val bounceFraction by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = SineToSineEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pin_bounce"
    )

    // Radiating light rays fraction (for Flashlight mode)
    val rayPulse by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ray_pulse"
    )

    // Rapid horizontal shaking animation (for Error state)
    val errorShake by infiniteTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 70, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "error_shake"
    )

    // Speaking acoustic ripple wave expansion
    val speechWaveProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "speech_wave"
    )

    // Time float for message / note particle rises
    val floatTime by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "float_time"
    )

    // Success expansion shockwave
    val successProgress by animateFloatAsState(
        targetValue = if (isSuccess) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = DecelerateEasing),
        label = "success_progress"
    )

    // Vector Painters for state icons (retained across frames)
    val phonePainter = rememberVectorPainter(Icons.Default.Phone)
    val msgPainter = rememberVectorPainter(Icons.Default.Comment)
    val playPainter = rememberVectorPainter(Icons.Default.PlayArrow)
    val pinPainter = rememberVectorPainter(Icons.Default.LocationOn)
    val notePainter = rememberVectorPainter(Icons.Default.MusicNote)

    // Floating particle system configuration
    val floatingParticles = remember {
        List(8) {
            FloatingParticle(
                xOffset = (-60..60).random().toFloat(),
                progress = Math.random().toFloat(),
                speed = 0.005f + Math.random().toFloat() * 0.01f,
                scale = 0.6f + Math.random().toFloat() * 0.5f
            )
        }
    }

    // Core Starfield / Accretion disk particles
    val particles = remember {
        List(28) { i ->
            OrbParticle(
                baseAngle = ((i * 360f / 28) * (Math.PI / 180f)).toFloat(),
                radiusFraction = 0.25f + (i % 6) * 0.12f,
                speedMultiplier = 0.6f + (i % 4) * 0.35f,
                size = 3.5f + (i % 5) * 1.2f
            )
        }
    }

    // Zero-allocation Canvas drawing
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
    ) {
        val width = size.width
        val height = size.height

        // Shake logic for Error state
        val currentShake = if (isError) errorShake * 10.dp.toPx() else 0f
        val shakeCenterX = width / 2f + currentShake
        val centerY = height / 2f
        val baseRadius = size.minDimension * 0.32f * currentScale

        // A. Background Glow aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = glowColors,
                center = Offset(shakeCenterX, centerY),
                radius = baseRadius * 1.85f
            ),
            radius = baseRadius * 1.85f,
            center = Offset(shakeCenterX, centerY)
        )

        // A.2. Immersive flowing oscilloscope sound visualizer waves
        if (isListening) {
            val waveAmplitudes = floatArrayOf(24.dp.toPx(), 16.dp.toPx(), 10.dp.toPx())
            val step = 8

            for (w in 0 until 3) {
                val path = wavePaths[w]
                path.reset()

                val amplitude = waveAmplitudes[w] * activeScale
                val frequency = waveFrequencies[w]
                val phase = waveOffset1 * (waveSpeeds[w] * 15f)

                path.moveTo(0f, centerY)
                for (x in 0..width.toInt() step step) {
                    val envelope = sin(x.toFloat() / width * Math.PI.toFloat())
                    val y = centerY + sin((x - currentShake) * frequency + phase) * amplitude * envelope
                    path.lineTo(x.toFloat(), y)
                }

                drawPath(
                    path = path,
                    color = waveColors[w],
                    style = Stroke(width = if (w == 0) 2.dp.toPx() else 1.dp.toPx())
                )
            }
        }

        // A.3. Resonant Speaking Acoustic Ripples (Expanding outward like speech soundwaves)
        if (isSpeaking) {
            val rippleRadius1 = baseRadius * (1.05f + speechWaveProgress * 0.65f)
            val rippleAlpha1 = (1f - speechWaveProgress).coerceIn(0f, 1f) * 0.6f
            drawCircle(
                color = animatedColor.copy(alpha = rippleAlpha1),
                radius = rippleRadius1,
                center = Offset(shakeCenterX, centerY),
                style = Stroke(width = 2.5.dp.toPx() * (1f - speechWaveProgress * 0.5f))
            )

            val offsetProgress = (speechWaveProgress + 0.5f) % 1f
            val rippleRadius2 = baseRadius * (1.05f + offsetProgress * 0.65f)
            val rippleAlpha2 = (1f - offsetProgress).coerceIn(0f, 1f) * 0.6f
            drawCircle(
                color = animatedColor.copy(alpha = rippleAlpha2),
                radius = rippleRadius2,
                center = Offset(shakeCenterX, centerY),
                style = Stroke(width = 2.dp.toPx() * (1f - offsetProgress * 0.5f))
            )
        }

        // B. Concentric HUD rings
        // Ring 1 (Dashed Outer Ring - rotates clockwise)
        rotate(degrees = ring1Rotation, pivot = Offset(shakeCenterX, centerY)) {
            drawCircle(
                color = animatedColor.copy(alpha = 0.4f),
                radius = baseRadius * 1.25f,
                center = Offset(shakeCenterX, centerY),
                style = Stroke(
                    width = 2.dp.toPx(),
                    pathEffect = dashEffectRing1
                )
            )

            // Inner Ring 1 tick marks
            drawCircle(
                color = animatedColor.copy(alpha = 0.3f),
                radius = baseRadius * 1.1f,
                center = Offset(shakeCenterX, centerY),
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = dashEffectRing1Inner
                )
            )
        }

        // Ring 2 (Dotted Inner Ring - rotates counter-clockwise)
        rotate(degrees = ring2Rotation, pivot = Offset(shakeCenterX, centerY)) {
            drawCircle(
                color = animatedColor.copy(alpha = 0.5f),
                radius = baseRadius * 1.2f,
                center = Offset(shakeCenterX, centerY),
                style = Stroke(
                    width = 3.dp.toPx(),
                    pathEffect = dashEffectRing2
                )
            )

            // Sci-fi arcs
            val arcSize = Size(baseRadius * 2.4f, baseRadius * 2.4f)
            val arcTopLeft = Offset(shakeCenterX - baseRadius * 1.2f, centerY - baseRadius * 1.2f)

            drawArc(
                color = animatedColor.copy(alpha = 0.6f),
                startAngle = 45f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx()),
                size = arcSize,
                topLeft = arcTopLeft
            )
            drawArc(
                color = animatedColor.copy(alpha = 0.6f),
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 3.dp.toPx()),
                size = arcSize,
                topLeft = arcTopLeft
            )
        }

        // Ring 3 (Outer Tech Ring with Ticks and Degree Marks)
        val ring3RotationRad = ring3Rotation * (Math.PI.toFloat() / 180f)
        for (deg in 0 until 360 step 15) {
            val rad = deg * (Math.PI.toFloat() / 180f)
            val isMajor = deg % 90 == 0
            val isMedium = deg % 45 == 0 && !isMajor
            val rStart = if (isMajor) baseRadius * 1.32f else if (isMedium) baseRadius * 1.36f else baseRadius * 1.39f
            val rEnd = if (isMajor) baseRadius * 1.48f else if (isMedium) baseRadius * 1.44f else baseRadius * 1.41f
            val colorAlpha = if (isMajor) 0.8f else if (isMedium) 0.5f else 0.3f

            val totalRad = rad + ring3RotationRad
            val cosRad = cos(totalRad)
            val sinRad = sin(totalRad)

            val x1 = shakeCenterX + rStart * cosRad
            val y1 = centerY + rStart * sinRad
            val x2 = shakeCenterX + rEnd * cosRad
            val y2 = centerY + rEnd * sinRad

            drawLine(
                color = animatedColor.copy(alpha = colorAlpha),
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
            )

            if (isMajor) {
                drawCircle(
                    color = animatedColor.copy(alpha = 0.9f),
                    radius = 2.dp.toPx(),
                    center = Offset(x2, y2)
                )
            }
        }

        // Ring 4 (Large Bracket Ring - rotates counter-clockwise)
        rotate(degrees = ring4Rotation, pivot = Offset(shakeCenterX, centerY)) {
            val bracketRadius = baseRadius * 1.55f
            val bracketDiameter = bracketRadius * 2
            val bracketSize = Size(bracketDiameter, bracketDiameter)
            val bracketTopLeft = Offset(shakeCenterX - bracketRadius, centerY - bracketRadius)

            for (i in 0 until 4) {
                val startAngle = i * 90f - 15f
                drawArc(
                    color = animatedColor.copy(alpha = 0.4f),
                    startAngle = startAngle,
                    sweepAngle = 30f,
                    useCenter = false,
                    style = Stroke(width = 1.5.dp.toPx()),
                    size = bracketSize,
                    topLeft = bracketTopLeft
                )

                // Tech notch
                val crossRad = (i * 90f) * (Math.PI.toFloat() / 180f)
                val cosCross = cos(crossRad)
                val sinCross = sin(crossRad)
                val notchStart = bracketRadius - 6.dp.toPx()
                val notchEnd = bracketRadius + 6.dp.toPx()

                drawLine(
                    color = animatedColor.copy(alpha = 0.7f),
                    start = Offset(shakeCenterX + notchStart * cosCross, centerY + notchStart * sinCross),
                    end = Offset(shakeCenterX + notchEnd * cosCross, centerY + notchEnd * sinCross),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }

        // B.3. Circular Sound Visualizer Corona Bars (When Listening or Speaking)
        if (isListening || isSpeaking) {
            val barCount = 48
            val innerRadius = baseRadius * 1.05f
            val outerMaxRadius = baseRadius * 1.30f
            val timeFactor = waveOffset1 * (if (isSpeaking) 6f else 4f)

            for (i in 0 until barCount) {
                val angleDeg = i * (360f / barCount)
                val angleRad = angleDeg * (Math.PI.toFloat() / 180f)

                val mockAmplitude = (
                    sin(angleDeg * 0.15f + timeFactor) * 0.4f +
                    cos(angleDeg * 0.3f - timeFactor * 0.8f) * 0.3f +
                    sin(angleDeg * 0.6f + timeFactor * 1.4f) * 0.3f
                ).coerceIn(-1.0f, 1.0f)

                val energy = (mockAmplitude + 1f) / 2f
                val barHeight = (outerMaxRadius - innerRadius) * energy * (if (isSpeaking) 1.2f else 1.0f)

                val cosAngle = cos(angleRad)
                val sinAngle = sin(angleRad)

                val startX = shakeCenterX + innerRadius * cosAngle
                val startY = centerY + innerRadius * sinAngle
                val endX = shakeCenterX + (innerRadius + barHeight) * cosAngle
                val endY = centerY + (innerRadius + barHeight) * sinAngle

                drawLine(
                    color = animatedColor.copy(alpha = 0.75f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // C. Central Plasma Wave Spheres (Reusing preallocated Paths)
        plasmaPath1.reset()
        plasmaPath2.reset()

        val steps = 60
        val angleStep = (2 * Math.PI / steps).toFloat()
        val deformMultiplier = if (isAiThinking) 1.8f else 1.0f

        for (i in 0..steps) {
            val angle = i * angleStep
            val offset1 = sin(angle * 5 + waveOffset1) * (baseRadius * 0.08f * deformMultiplier)
            val offset2 = cos(angle * 4 + waveOffset2) * (baseRadius * 0.06f * deformMultiplier)

            val r1 = baseRadius * 0.85f + offset1
            val r2 = baseRadius * 0.75f + offset2

            val cosA = cos(angle)
            val sinA = sin(angle)

            val x1 = shakeCenterX + r1 * cosA
            val y1 = centerY + r1 * sinA
            val x2 = shakeCenterX + r2 * cosA
            val y2 = centerY + r2 * sinA

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

        // Layer 1 Plasma
        drawPath(
            path = plasmaPath1,
            brush = Brush.radialGradient(
                colors = plasma1Colors,
                center = Offset(shakeCenterX, centerY),
                radius = baseRadius
            )
        )
        drawPath(
            path = plasmaPath1,
            color = animatedColor.copy(alpha = 0.8f),
            style = Stroke(width = 2.dp.toPx())
        )

        // Layer 2 Plasma
        drawPath(
            path = plasmaPath2,
            brush = Brush.radialGradient(
                colors = plasma2Colors,
                center = Offset(shakeCenterX, centerY),
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
                colors = coreColors,
                center = Offset(shakeCenterX, centerY),
                radius = baseRadius * (if (isAiThinking) 0.6f else 0.5f)
            ),
            radius = baseRadius * (if (isAiThinking) 0.6f else 0.5f),
            center = Offset(shakeCenterX, centerY)
        )

        // E. Floating Sci-Fi Particles (Deterministic, zero mutations in drawScope)
        val spinRad = particleSpin * (Math.PI.toFloat() / 180f)

        particles.forEach { p ->
            val angle = p.baseAngle + spinRad * p.speedMultiplier

            // Thinking: Inward vortex accretion spiral
            // Success: Outward burst
            val currentRadiusFraction = when {
                isAiThinking -> {
                    val spiralProgress = (p.radiusFraction - floatTime * 0.6f) % 1.0f
                    if (spiralProgress < 0f) spiralProgress + 1.0f else spiralProgress
                }
                isSuccess -> {
                    p.radiusFraction + successProgress * 1.5f
                }
                else -> {
                    p.radiusFraction
                }
            }

            val pRadius = baseRadius * currentRadiusFraction
            val px = shakeCenterX + pRadius * cos(angle)
            val py = centerY + pRadius * sin(angle)

            val pAlpha = if (isSuccess) {
                (1f - successProgress).coerceAtLeast(0f) * 0.75f
            } else {
                0.75f
            }

            drawCircle(
                color = animatedColor.copy(alpha = pAlpha),
                radius = p.size / 2f,
                center = Offset(px, py)
            )
        }

        // ==========================================
        // 🚀 CUSTOM TASK ANIMATIONS
        // ==========================================
        if (lastAction != null && !isSuccess && !isListening && !isAiThinking) {
            val actionUpper = lastAction.uppercase()

            when {
                // 📞 Calling (Phone icon orbits the orb)
                actionUpper == "CALL" || actionUpper == "SMS" -> {
                    val iconSize = 24.dp.toPx()
                    val orbitRadius = baseRadius * 1.4f
                    val orbitAngleRad = orbitAngle * (Math.PI.toFloat() / 180f)
                    val iconX = shakeCenterX + orbitRadius * cos(orbitAngleRad) - iconSize / 2
                    val iconY = centerY + orbitRadius * sin(orbitAngleRad) - iconSize / 2

                    drawCircle(
                        color = animatedColor.copy(alpha = 0.25f),
                        radius = orbitRadius,
                        center = Offset(shakeCenterX, centerY),
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = dashEffectOrbit
                        )
                    )

                    translate(left = iconX, top = iconY) {
                        with(phonePainter) {
                            draw(
                                size = Size(iconSize, iconSize),
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        }
                    }
                }

                // 💬 WhatsApp (Message bubbles float up)
                actionUpper == "WHATSAPP_MSG" -> {
                    val iconSize = 18.dp.toPx()
                    floatingParticles.forEach { p ->
                        val currentProgress = (p.progress + floatTime) % 1.0f
                        val y = centerY + baseRadius * (1.1f - currentProgress * 2.2f)
                        val x = shakeCenterX + p.xOffset.dp.toPx() * p.scale + sin(currentProgress * Math.PI.toFloat() * 3) * 16.dp.toPx()
                        val alpha = (1.0f - currentProgress) * sin(currentProgress * Math.PI.toFloat()).toFloat()

                        if (currentProgress in 0f..1.0f) {
                            translate(left = x - (iconSize * p.scale) / 2, top = y - (iconSize * p.scale) / 2) {
                                with(msgPainter) {
                                    draw(
                                        size = Size(iconSize * p.scale, iconSize * p.scale),
                                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = alpha.coerceIn(0f, 1f)))
                                    )
                                }
                            }
                        }
                    }
                }

                // ▶️ YouTube (Play button pulses)
                actionUpper == "YOUTUBE_SEARCH" -> {
                    val iconSize = 32.dp.toPx() * playPulse
                    val iconX = shakeCenterX - iconSize / 2
                    val iconY = centerY - iconSize / 2
                    translate(left = iconX, top = iconY) {
                        with(playPainter) {
                            draw(
                                size = Size(iconSize, iconSize),
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        }
                    }
                }

                // ⏰ Alarm Set (Clock hands spin)
                actionUpper == "SET_ALARM" || actionUpper == "SET_TIMER" -> {
                    val clockRadius = 22.dp.toPx()
                    drawCircle(
                        color = Color.White.copy(alpha = 0.8f),
                        radius = clockRadius,
                        center = Offset(shakeCenterX, centerY),
                        style = Stroke(width = 2.dp.toPx())
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.5.dp.toPx(),
                        center = Offset(shakeCenterX, centerY)
                    )
                    // Minute Hand
                    val minRad = clockHandRotation * (Math.PI.toFloat() / 180f)
                    val minLen = clockRadius * 0.75f
                    drawLine(
                        color = Color.White,
                        start = Offset(shakeCenterX, centerY),
                        end = Offset(shakeCenterX + minLen * cos(minRad), centerY + minLen * sin(minRad)),
                        strokeWidth = 2.dp.toPx()
                    )
                    // Hour Hand (12x slower)
                    val hourRad = (clockHandRotation / 12f) * (Math.PI.toFloat() / 180f)
                    val hourLen = clockRadius * 0.45f
                    drawLine(
                        color = Color.White,
                        start = Offset(shakeCenterX, centerY),
                        end = Offset(shakeCenterX + hourLen * cos(hourRad), centerY + hourLen * sin(hourRad)),
                        strokeWidth = 3.dp.toPx()
                    )
                }

                // 📍 Location (Map pin bounces)
                actionUpper == "LOCATION" -> {
                    val iconSize = 28.dp.toPx()
                    val bounceOffset = -14.dp.toPx() * bounceFraction
                    val iconX = shakeCenterX - iconSize / 2
                    val iconY = centerY - baseRadius - iconSize + bounceOffset

                    translate(left = iconX, top = iconY) {
                        with(pinPainter) {
                            draw(
                                size = Size(iconSize, iconSize),
                                colorFilter = ColorFilter.tint(Color.White)
                            )
                        }
                    }
                }

                // 🔦 Flashlight (Light rays radiate)
                actionUpper == "FLASHLIGHT" -> {
                    val rayCount = 8
                    val innerR = baseRadius * 1.05f
                    val outerR = baseRadius * (1.15f + 0.18f * rayPulse)
                    for (i in 0 until rayCount) {
                        val angleRad = (i * 360f / rayCount) * (Math.PI.toFloat() / 180f)
                        val cosA = cos(angleRad)
                        val sinA = sin(angleRad)
                        val startX = shakeCenterX + innerR * cosA
                        val startY = centerY + innerR * sinA
                        val endX = shakeCenterX + outerR * cosA
                        val endY = centerY + outerR * sinA

                        drawLine(
                            color = Color(0xFFFFD600).copy(alpha = 0.8f * rayPulse),
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = 3.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                }

                // 🎵 Music (Musical notes float)
                actionUpper == "PLAY_MUSIC" -> {
                    val iconSize = 20.dp.toPx()
                    floatingParticles.forEach { p ->
                        val currentProgress = (p.progress + floatTime) % 1.0f
                        val y = centerY + baseRadius * (1.1f - currentProgress * 2.2f)
                        val x = shakeCenterX + p.xOffset.dp.toPx() * p.scale + sin(currentProgress * Math.PI.toFloat() * 3) * 16.dp.toPx()
                        val alpha = (1.0f - currentProgress) * sin(currentProgress * Math.PI.toFloat()).toFloat()

                        if (currentProgress in 0f..1.0f) {
                            translate(left = x - (iconSize * p.scale) / 2, top = y - (iconSize * p.scale) / 2) {
                                with(notePainter) {
                                    draw(
                                        size = Size(iconSize * p.scale, iconSize * p.scale),
                                        colorFilter = ColorFilter.tint(Color.White.copy(alpha = alpha.coerceIn(0f, 1f)))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ==========================================
        // ✅ SUCCESS EXPLOSION SHOCKWAVE (Task Done)
        // ==========================================
        if (successProgress > 0f) {
            drawCircle(
                color = Color(0xFF00FF88).copy(alpha = 0.6f * (1f - successProgress)),
                radius = baseRadius * (1f + successProgress * 1.5f),
                center = Offset(shakeCenterX, centerY),
                style = Stroke(
                    width = 4.dp.toPx() * (1f - successProgress),
                    cap = StrokeCap.Round
                )
            )
        }

        // ==========================================
        // ❌ RED FLASHING GLOW (Error state)
        // ==========================================
        if (isError) {
            val flashAlpha = 0.25f * sin(floatTime * Math.PI.toFloat() * 6).coerceAtLeast(0f)
            drawCircle(
                color = Color(0xFFFF3B30).copy(alpha = flashAlpha),
                radius = baseRadius * 1.35f,
                center = Offset(shakeCenterX, centerY)
            )
        }
    }
}

// Particle helper class for floating icons/notes
private class FloatingParticle(
    val xOffset: Float,
    val progress: Float,
    val speed: Float,
    val scale: Float
)

// Starfield / Accretion particle configuration
private class OrbParticle(
    val baseAngle: Float,
    val radiusFraction: Float,
    val speedMultiplier: Float,
    val size: Float
)

// Linear custom interpolator for breathing animation
private val SineToSineEasing = Easing { fraction ->
    sin(fraction * Math.PI).toFloat()
}

// File: app/src/main/java/com/myai/assistant/ui/theme/Color.kt
// App Color Palette — Dark futuristic AI theme

package com.myai.assistant.ui.theme

import androidx.compose.ui.graphics.Color

// ═══════════════════════════════════════
// PRIMARY — Electric Blue (AI vibe)
// ═══════════════════════════════════════
val PrimaryLight = Color(0xFF3B82F6)        // Bright blue
val PrimaryDark = Color(0xFF60A5FA)         // Lighter blue for dark theme
val PrimaryContainer = Color(0xFF1E3A5F)    // Deep blue container
val OnPrimaryContainer = Color(0xFFD6E4FF)  // Text on container

// ═══════════════════════════════════════
// SECONDARY — Cyan / Teal (Accent)
// ═══════════════════════════════════════
val SecondaryLight = Color(0xFF06B6D4)      // Cyan
val SecondaryDark = Color(0xFF22D3EE)       // Bright cyan
val SecondaryContainer = Color(0xFF0E3D4A)  // Deep teal container
val OnSecondaryContainer = Color(0xFFB8F0FF)

// ═══════════════════════════════════════
// TERTIARY — Purple / Violet (AI glow)
// ═══════════════════════════════════════
val TertiaryLight = Color(0xFF8B5CF6)       // Violet
val TertiaryDark = Color(0xFFA78BFA)        // Light violet
val TertiaryContainer = Color(0xFF2E1A5E)   // Deep purple
val OnTertiaryContainer = Color(0xFFE8DEFF)

// ═══════════════════════════════════════
// BACKGROUND & SURFACE — Dark Mode
// ═══════════════════════════════════════
val BackgroundDark = Color(0xFF0A0E1A)      // Almost black with blue tint
val SurfaceDark = Color(0xFF111827)         // Dark card surface
val SurfaceVariantDark = Color(0xFF1F2937)  // Slightly lighter surface
val SurfaceContainerDark = Color(0xFF151B2B) // Container background

// Light theme backgrounds
val BackgroundLight = Color(0xFFF8FAFC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceVariantLight = Color(0xFFF1F5F9)

// ═══════════════════════════════════════
// TEXT COLORS
// ═══════════════════════════════════════
val OnBackgroundDark = Color(0xFFE2E8F0)    // Light gray text
val OnBackgroundLight = Color(0xFF0F172A)   // Dark text
val OnSurfaceDark = Color(0xFFF1F5F9)       // White-ish text
val OnSurfaceLight = Color(0xFF1E293B)      // Dark text
val OnSurfaceVariantDark = Color(0xFF94A3B8) // Muted text (dark)
val OnSurfaceVariantLight = Color(0xFF64748B) // Muted text (light)

// ═══════════════════════════════════════
// ERROR / SUCCESS / WARNING
// ═══════════════════════════════════════
val ErrorColor = Color(0xFFEF4444)          // Red
val ErrorContainer = Color(0xFF3B1111)      // Dark red container
val SuccessColor = Color(0xFF22C55E)        // Green
val WarningColor = Color(0xFFF59E0B)        // Amber

// ═══════════════════════════════════════
// SPECIAL — Gradients ke liye
// ═══════════════════════════════════════
val GradientStart = Color(0xFF3B82F6)       // Blue
val GradientMiddle = Color(0xFF8B5CF6)      // Purple
val GradientEnd = Color(0xFF06B6D4)         // Cyan

// Chat bubble colors
val UserBubble = Color(0xFF3B82F6)          // Blue bubble (user)
val AiBubble = Color(0xFF1F2937)            // Dark bubble (AI)
val AiBubbleLight = Color(0xFFF1F5F9)       // Light bubble (AI)

// Voice button glow
val VoiceActiveGlow = Color(0xFF3B82F6)
val VoiceListeningGlow = Color(0xFFEF4444)  // Red when listening

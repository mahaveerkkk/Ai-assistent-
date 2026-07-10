// File: app/src/main/java/com/myai/assistant/ui/theme/Theme.kt
// App Theme — Dark (default) + Light mode support

package com.myai.assistant.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ═══════════════════════════════════════
// DARK COLOR SCHEME — Primary theme (AI wali feel)
// ═══════════════════════════════════════
private val DarkColorScheme = darkColorScheme(
    primary = PrimaryDark,
    onPrimary = Color.White,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,

    secondary = SecondaryDark,
    onSecondary = Color.White,
    secondaryContainer = SecondaryContainer,
    onSecondaryContainer = OnSecondaryContainer,

    tertiary = TertiaryDark,
    onTertiary = Color.White,
    tertiaryContainer = TertiaryContainer,
    onTertiaryContainer = OnTertiaryContainer,

    background = BackgroundDark,
    onBackground = OnBackgroundDark,

    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainer = SurfaceContainerDark,

    error = ErrorColor,
    errorContainer = ErrorContainer,
    onError = Color.White,
    onErrorContainer = Color(0xFFFFDAD6),

    outline = Color(0xFF334155),
    outlineVariant = Color(0xFF1E293B),
    inverseSurface = Color(0xFFE2E8F0),
    inverseOnSurface = Color(0xFF1E293B),
    inversePrimary = PrimaryLight,
    scrim = Color.Black
)

// ═══════════════════════════════════════
// LIGHT COLOR SCHEME
// ═══════════════════════════════════════
private val LightColorScheme = lightColorScheme(
    primary = PrimaryLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A5F),

    secondary = SecondaryLight,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF0E3D4A),

    tertiary = TertiaryLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF2E1A5E),

    background = BackgroundLight,
    onBackground = OnBackgroundLight,

    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,

    error = ErrorColor,
    errorContainer = Color(0xFFFEE2E2),
    onError = Color.White,
    onErrorContainer = Color(0xFF7F1D1D),

    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFE2E8F0),
    inverseSurface = Color(0xFF1E293B),
    inverseOnSurface = Color(0xFFF1F5F9),
    inversePrimary = PrimaryDark,
    scrim = Color.Black
)

// ═══════════════════════════════════════
// THEME COMPOSABLE
// ═══════════════════════════════════════
@Composable
fun MyAIAssistantTheme(
    chatTheme: String = "Modern Blue",
    content: @Composable () -> Unit
) {
    val colorScheme = when (chatTheme) {
        "Cyberpunk Purple" -> darkColorScheme(
            primary = Color(0xFFEC4899),      // Pink
            onPrimary = Color.White,
            secondary = Color(0xFFA855F7),    // Purple
            tertiary = Color(0xFF06B6D4),     // Cyan
            background = Color(0xFF090514),   // Deep purple-black
            surface = Color(0xFF130E26),
            surfaceVariant = Color(0xFF1E1736),
            onBackground = Color(0xFFF3E8FF),
            onSurface = Color(0xFFF3E8FF)
        )
        "Minimalist Dark" -> darkColorScheme(
            primary = Color(0xFFF1F5F9),      // Off-white
            onPrimary = Color(0xFF0F172A),
            secondary = Color(0xFF94A3B8),    // Muted grey
            tertiary = Color(0xFF475569),     // Slate
            background = Color(0xFF09090B),   // Zinc black
            surface = Color(0xFF18181B),
            surfaceVariant = Color(0xFF27272A),
            onBackground = Color(0xFFF4F4F5),
            onSurface = Color(0xFFF4F4F5)
        )
        "Forest Green" -> darkColorScheme(
            primary = Color(0xFF10B981),      // Emerald green
            onPrimary = Color.White,
            secondary = Color(0xFF34D399),    // Mint
            tertiary = Color(0xFF064E3B),     // Dark forest
            background = Color(0xFF050F0A),   // Forest black
            surface = Color(0xFF0A1C14),
            surfaceVariant = Color(0xFF102A1E),
            onBackground = Color(0xFFECFDF5),
            onSurface = Color(0xFFECFDF5)
        )
        else -> DarkColorScheme // Modern Blue
    }

    // Status bar aur navigation bar colors set karo
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

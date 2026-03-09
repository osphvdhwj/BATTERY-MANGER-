package com.crdroid.batterywellbeing.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Futuristic AMOLED Color Palette
val AmoledBlack = Color(0xFF000000)
val GlassSurface = Color(0xFF151515) // Extremely dark gray for cards
val NeonCyan = Color(0xFF00E5FF)
val NeonBlue = Color(0xFF007BFF)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFA0A0A0)

private val AmoledColorScheme = darkColorScheme(
    background = AmoledBlack,
    surface = GlassSurface,
    primary = NeonCyan,
    secondary = NeonBlue,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun BatteryWellbeingTheme(
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = AmoledBlack.toArgb()
            window.navigationBarColor = AmoledBlack.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = AmoledColorScheme,
        content = content
    )
}

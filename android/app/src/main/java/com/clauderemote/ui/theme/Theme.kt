package com.clauderemote.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Terminal colors
val TerminalBackground = Color(0xFF1A1A1A)
val TerminalGreen = Color(0xFF00FF00)
val TerminalRed = Color(0xFFFF4444)
val TerminalGray = Color(0xFF888888)
val TerminalWhite = Color(0xFFE0E0E0)

// App colors
val Primary = Color(0xFF6366F1)
val PrimaryVariant = Color(0xFF4F46E5)
val Secondary = Color(0xFF10B981)
val Background = Color(0xFF0F0F0F)
val Surface = Color(0xFF1F1F1F)
val SurfaceVariant = Color(0xFF2F2F2F)
val OnBackground = Color(0xFFE5E5E5)
val OnSurface = Color(0xFFE5E5E5)
val Error = Color(0xFFEF4444)

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    secondary = Secondary,
    background = Background,
    surface = Surface,
    surfaceVariant = SurfaceVariant,
    onBackground = OnBackground,
    onSurface = OnSurface,
    error = Error
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    secondary = Secondary,
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    error = Error
)

@Composable
fun ClaudeRemoteTheme(
    darkTheme: Boolean = true, // Always dark for terminal feel
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

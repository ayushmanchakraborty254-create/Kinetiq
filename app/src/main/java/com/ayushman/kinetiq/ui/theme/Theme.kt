package com.ayushman.kinetiq.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = KinetiqLavender,
    onPrimary = KinetiqObsidian,
    primaryContainer = KinetiqLavenderMuted,
    onPrimaryContainer = KinetiqOffWhite,
    secondary = KinetiqSage,
    onSecondary = KinetiqObsidian,
    background = KinetiqObsidian,
    onBackground = KinetiqOffWhite,
    surface = KinetiqCharcoalDark,
    onSurface = KinetiqOffWhite,
    surfaceVariant = KinetiqCharcoalCard,
    onSurfaceVariant = KinetiqTextMuted,
    outline = KinetiqBorder
)

@Composable
fun KinetiqTheme(
    darkTheme: Boolean = true, // KINETIQ is designed primarily as a refined dark obsidian interface
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = KinetiqObsidian.toArgb()
                window.navigationBarColor = KinetiqObsidian.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}


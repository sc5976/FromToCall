package com.fromtocall.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkBase = darkColorScheme(
    secondary = Secondary,
    onSecondary = OnSecondary,
    secondaryContainer = SecondaryVariant,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    error = Error,
    onError = OnError
)

@Composable
fun FromToCallTheme(
    accentKey: String = "green",
    content: @Composable () -> Unit
) {
    val accent = AccentPalette.byKey(accentKey)
    val colorScheme = DarkBase.copy(
        primary = accent.primary,
        onPrimary = accent.onPrimary,
        primaryContainer = accent.primaryContainer
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

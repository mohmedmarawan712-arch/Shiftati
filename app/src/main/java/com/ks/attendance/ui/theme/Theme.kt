package com.ks.attendance.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary              = AmoledPrimary,
    onPrimary            = AmoledOnPrimary,
    primaryContainer     = AmoledPrimaryContainer,
    onPrimaryContainer   = AmoledOnPrimaryContainer,
    secondary            = AmoledSecondary,
    onSecondary          = AmoledOnSecondary,
    secondaryContainer   = AmoledSecondaryContainer,
    onSecondaryContainer = AmoledOnSecondaryContainer,
    background           = AmoledBackground,      // True 100% AMOLED Black
    onBackground         = AmoledTextPrimary,
    surface              = AmoledSurface,         // Subtle 0x0A for contrast
    onSurface            = AmoledTextPrimary,
    surfaceVariant       = AmoledSurfaceVariant,
    onSurfaceVariant     = AmoledTextSecondary,
    outline              = AmoledOutline,
    error                = Color(0xFFF87171),
    onError              = Color(0xFF450A0A)
)

private val LightColorScheme = lightColorScheme(
    primary              = LightPrimary,
    onPrimary            = LightOnPrimary,
    primaryContainer     = LightPrimaryContainer,
    onPrimaryContainer   = LightOnPrimaryContainer,
    secondary            = LightSecondary,
    onSecondary          = LightOnSecondary,
    secondaryContainer   = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    background           = LightBackground,
    onBackground         = LightTextPrimary,
    surface              = LightSurface,
    onSurface            = LightTextPrimary,
    surfaceVariant       = LightSurfaceVariant,
    onSurfaceVariant     = LightTextSecondary,
    outline              = LightOutline,
    error                = Color(0xFFDC2626),
    onError              = Color(0xFFFFFFFF)
)

@Composable
fun KSAttendanceTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}

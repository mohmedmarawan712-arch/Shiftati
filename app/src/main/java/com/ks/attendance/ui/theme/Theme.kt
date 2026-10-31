package com.ks.attendance.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary          = Primary80,
    onPrimary        = Color(0xFF003729),
    primaryContainer = PrimaryContainer80,
    onPrimaryContainer = PrimaryContainer40,
    secondary        = Secondary80,
    onSecondary      = Color(0xFF1E3D31),
    secondaryContainer = SecondaryContainer80,
    onSecondaryContainer = SecondaryContainer40,
    tertiary         = Tertiary80,
    onTertiary       = Color(0xFF003550),
    error            = Error80,
    onError          = Color(0xFF690005),
    background       = NeutralDark,
    onBackground     = Color(0xFFE1E3E0),
    surface          = Color(0xFF191C1B),
    onSurface        = Color(0xFFE1E3E0),
    surfaceVariant   = Color(0xFF3F4945),
    onSurfaceVariant = Color(0xFFBEC9C4),
    outline          = Color(0xFF899490)
)

private val LightColorScheme = lightColorScheme(
    primary          = Primary40,
    onPrimary        = Color(0xFFFFFFFF),
    primaryContainer = PrimaryContainer40,
    onPrimaryContainer = Color(0xFF002118),
    secondary        = Secondary40,
    onSecondary      = Color(0xFFFFFFFF),
    secondaryContainer = SecondaryContainer40,
    onSecondaryContainer = Color(0xFF062019),
    tertiary         = Tertiary40,
    onTertiary       = Color(0xFFFFFFFF),
    error            = Error40,
    onError          = Color(0xFFFFFFFF),
    background       = NeutralLight,
    onBackground     = Color(0xFF191C1B),
    surface          = Color(0xFFF5FAF7),
    onSurface        = Color(0xFF191C1B),
    surfaceVariant   = Color(0xFFDBE5DF),
    onSurfaceVariant = Color(0xFF3F4945),
    outline          = Color(0xFF6F7975)
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
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = AppTypography,
        content     = content
    )
}



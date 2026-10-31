package com.ks.attendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.navigation.KSNavGraph
import com.ks.attendance.ui.theme.KSAttendanceTheme
import com.ks.attendance.utils.PrefsUtil

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val app = application as KSApp
        MainViewModel.Factory(app.repository, app.prefs, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by viewModel.settingsState.collectAsState()
            val systemInDark = isSystemInDarkTheme()
            val isDark = when (settings.themeMode) {
                PrefsUtil.THEME_LIGHT -> false
                PrefsUtil.THEME_DARK  -> true
                else                  -> systemInDark
            }
            val layoutDirection = if (settings.language == "en") LayoutDirection.Ltr else LayoutDirection.Rtl
            var showSplash by remember { mutableStateOf(true) }

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                KSAttendanceTheme(darkTheme = isDark) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = if (isDark) Color.Black else MaterialTheme.colorScheme.background
                    ) {
                        if (showSplash) {
                            com.ks.attendance.ui.screens.splash.SplashScreen(
                                isDark = isDark,
                                onSplashFinished = { showSplash = false }
                            )
                        } else {
                            KSNavGraph(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

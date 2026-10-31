package com.ks.attendance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.navigation.KSNavGraph
import com.ks.attendance.ui.theme.KSAttendanceTheme

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
            val layoutDirection = if (settings.language == "en") LayoutDirection.Ltr else LayoutDirection.Rtl

            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                KSAttendanceTheme(darkTheme = settings.darkMode) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        KSNavGraph(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

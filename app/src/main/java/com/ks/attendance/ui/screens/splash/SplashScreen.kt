package com.ks.attendance.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ks.attendance.R
import com.ks.attendance.ui.theme.ColorWorked
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    isDark: Boolean,
    onSplashFinished: () -> Unit
) {
    val scale = remember { Animatable(0.88f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Run entrance animations simultaneously for a quick, snappy splash
        coroutineScope {
            launch {
                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(280, easing = FastOutSlowInEasing)
                )
            }
            launch {
                alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(240, easing = FastOutSlowInEasing)
                )
            }
        }
        delay(320L)
        onSplashFinished()
    }

    val bgColor = if (isDark) Color(0xFF000000) else Color(0xFFF8FAFC)
    val textColor = if (isDark) Color.White else Color(0xFF0F172A)
    val subtitleColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .scale(scale.value)
                .alpha(alpha.value)
                .padding(24.dp)
        ) {
            // App Icon Container
            Image(
                painter = painterResource(id = R.drawable.app_icon),
                contentDescription = "شفتاتي",
                modifier = Modifier
                    .size(112.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // App Name
            Text(
                text = "شفتاتي",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Subtitle
            Text(
                text = "إدارة الورديات ومتابعة الراتب",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = subtitleColor
            )
        }

        // Bottom version watermark
        Text(
            text = "Shiftati v2.0",
            style = MaterialTheme.typography.labelSmall,
            color = subtitleColor.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        )
    }
}

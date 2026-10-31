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
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    isDark: Boolean,
    onSplashFinished: () -> Unit
) {
    val scale = remember { Animatable(0.75f) }
    val alpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Animate entrance
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
        alpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(500, easing = FastOutSlowInEasing)
        )
        // Keep visible for pleasant brand perception
        delay(950L)
        onSplashFinished()
    }

    val bgColor = if (isDark) Color(0xFF000000) else Color(0xFFF7FBF9)
    val textColor = if (isDark) Color.White else Color(0xFF151D1A)
    val subtitleColor = if (isDark) Color(0xFF8E9793) else Color(0xFF626B67)

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
            Surface(
                shape = RoundedCornerShape(32.dp),
                color = if (isDark) Color(0xFF072018) else Color(0xFFE4F7EE),
                modifier = Modifier
                    .size(108.dp)
                    .border(
                        width = 1.5.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ColorWorked.copy(alpha = 0.8f),
                                ColorWorked.copy(alpha = 0.2f)
                            )
                        ),
                        shape = RoundedCornerShape(32.dp)
                    ),
                shadowElevation = if (isDark) 0.dp else 6.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = ColorWorked,
                        modifier = Modifier.size(52.dp)
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(14.dp)
                            .size(24.dp)
                            .background(ColorWorked, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = null,
                            tint = if (isDark) Color.Black else Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

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

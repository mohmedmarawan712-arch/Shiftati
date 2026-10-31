package com.ks.attendance.ui.screens.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.components.EmptyState
import com.ks.attendance.ui.components.LoadingOverlay
import com.ks.attendance.ui.components.StatCard
import com.ks.attendance.ui.theme.ColorWorked
import com.ks.attendance.utils.PayrollUtil
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val homeState by viewModel.homeState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Elapsed timer state
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    LaunchedEffect(homeState.openSession) {
        val session = homeState.openSession
        if (session != null) {
            while (true) {
                elapsedSeconds = (System.currentTimeMillis() - session.checkInTime) / 1000L
                delay(1000L)
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    // Show snackbar messages
    LaunchedEffect(homeState.message) {
        val msg = homeState.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(
            message = msg.text,
            duration = SnackbarDuration.Short
        )
        viewModel.clearMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("إضافة يدوي") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { onNavigate("record/-1") }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ─── Header: date ─────────────────────────────────────────────
                item {
                    DateHeader()
                }

                // ─── Clock In/Out button ─────────────────────────────────────
                item {
                    ClockButton(
                        isClockedIn = homeState.openSession != null,
                        elapsedSeconds = elapsedSeconds,
                        onClockIn = { viewModel.clockIn() },
                        onClockOut = { viewModel.clockOut() }
                    )
                }

                // ─── Period badge ─────────────────────────────────────────────
                item {
                    if (homeState.periodStart.isNotEmpty()) {
                        PeriodBadge(
                            start = homeState.periodStart,
                            end = homeState.periodEnd
                        )
                    }
                }

                // ─── Stat cards ───────────────────────────────────────────────
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatCard(
                            label = "اليوم",
                            value = PayrollUtil.formatHours(homeState.todayHours),
                            icon = Icons.Default.Schedule,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "الشهر",
                            value = PayrollUtil.formatHours(homeState.monthHours),
                            icon = Icons.Default.CalendarMonth,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                        StatCard(
                            label = "الراتب",
                            value = "${homeState.monthSalary.toInt()} ج",
                            subtitle = "تقريبي",
                            icon = Icons.Default.Payments,
                            color = Color(0xFF4CAF50),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // ─── Today's records title ─────────────────────────────────
                item {
                    Text(
                        text = "سجلات اليوم",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                // ─── Today's records list ─────────────────────────────────
                if (homeState.todayRecords.isEmpty()) {
                    item {
                        EmptyState(
                            message = "لا توجد سجلات اليوم",
                            icon = Icons.Default.EventBusy
                        )
                    }
                } else {
                    items(homeState.todayRecords, key = { it.id }) { record ->
                        RecordChip(
                            record = record,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            // Loading overlay
            LoadingOverlay(isVisible = homeState.isLoading)
        }
    }
}

@Composable
private fun DateHeader() {
    val now = remember { Date() }
    val arabicDate = remember {
        SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(now)
    }
    val englishDate = remember {
        SimpleDateFormat("EEEE, MMMM d yyyy", Locale.ENGLISH).format(now)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = arabicDate,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Text(
            text = englishDate,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ClockButton(
    isClockedIn: Boolean,
    elapsedSeconds: Long,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit
) {
    val clockedInStart = MaterialTheme.colorScheme.error
    val clockedInEnd = Color(0xFFFF6B6B)
    val clockedOutStart = MaterialTheme.colorScheme.primary
    val clockedOutEnd = Color(0xFF4DD9AC)

    val gradientColors = if (isClockedIn) {
        listOf(clockedInStart, clockedInEnd)
    } else {
        listOf(clockedOutStart, clockedOutEnd)
    }

    val animatedContainerColor by animateColorAsState(
        targetValue = if (isClockedIn) MaterialTheme.colorScheme.error
                      else MaterialTheme.colorScheme.primary,
        animationSpec = tween(600),
        label = "clockButtonColor"
    )

    val hours = elapsedSeconds / 3600
    val minutes = (elapsedSeconds % 3600) / 60
    val seconds = elapsedSeconds % 60
    val timerText = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.linearGradient(gradientColors)
                ),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = if (isClockedIn) onClockOut else onClockIn,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                elevation = null
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isClockedIn) Icons.Default.StopCircle
                                      else Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                    Text(
                        text = if (isClockedIn) "تسجيل الانصراف" else "تسجيل الحضور",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    if (isClockedIn) {
                        Text(
                            text = timerText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodBadge(start: String, end: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(50.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "من $start إلى $end",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
fun RecordChip(
    record: AttendanceRecord,
    modifier: Modifier = Modifier
) {
    val checkIn = PayrollUtil.formatTime(record.checkInTime)
    val checkOut = record.checkOutTime?.let { PayrollUtil.formatTime(it) } ?: "مفتوح"
    val isOpen = record.checkOutTime == null
    val sourceBadge = if (record.source == "ess_online") "ESS" else "يدوي"

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (isOpen) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.AccessTime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = "$checkIn – $checkOut",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (!isOpen) {
                Text(
                    text = PayrollUtil.formatHours(record.hoursWorked),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = if (record.source == "ess_online") MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    text = sourceBadge,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

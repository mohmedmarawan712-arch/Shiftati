package com.ks.attendance.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.ks.attendance.ui.components.ConfirmDeleteDialog
import com.ks.attendance.ui.components.EmptyState
import com.ks.attendance.ui.components.KSTopBar
import com.ks.attendance.ui.theme.*
import com.ks.attendance.utils.DateUtil
import com.ks.attendance.utils.PayrollUtil
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val homeState by viewModel.homeState.collectAsState()
    // Live elapsed timer for active session
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var recordToDelete by remember { mutableStateOf<AttendanceRecord?>(null) }
    var recordToRevert by remember { mutableStateOf<AttendanceRecord?>(null) }
    var isReverting by remember { mutableStateOf(false) }
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

    val isClockedIn = homeState.openSession != null

    Scaffold(
        topBar = {
            KSTopBar(
                title = "شفتاتي",
                onBack = null,
                actions = {
                    IconButton(onClick = { viewModel.syncFromEss {} }) {
                        Icon(
                            Icons.Filled.Sync,
                            contentDescription = "مزامنة سريعة",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigate("record/-1") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة وردية يدوياً")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
        ) {
            // ─── Header ───────────────────────────────────────────────────────
            item {
                Column {
                    val greeting = if (homeState.employeeName.isNotBlank())
                        "أهلاً، ${homeState.employeeName}"
                    else
                        "مرحباً بك 👋"
                    Text(
                        text = greeting,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = DateUtil.toArabicFull(DateUtil.today()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ─── Hero Clock In/Out Card ───────────────────────────────────────
            item {
                HeroClockCard(
                    isClockedIn = isClockedIn,
                    elapsedSeconds = elapsedSeconds,
                    checkInTime = homeState.openSession?.checkInTime,
                    onClockToggle = {
                        if (isClockedIn) viewModel.clockOut() else viewModel.clockIn()
                    }
                )
            }

            // ─── Today's Log Header ───────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ورديات اليوم",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // ─── Today's Records List ─────────────────────────────────────────
            if (homeState.todayRecords.isEmpty()) {
                item {
                    EmptyState(
                        message = "لم تسجل أي وردية اليوم حتى الآن",
                        icon = Icons.Filled.HistoryToggleOff
                    )
                }
            } else {
                items(homeState.todayRecords, key = { it.id }) { record ->
                    CompactRecordCard(
                        record = record,
                        is24Hour = homeState.is24Hour,
                        onClick = { onNavigate("record/${record.id}") },
                        onDelete = { recordToDelete = record },
                        onRevert = { recordToRevert = record }
                    )
                }
            }
        }

        // Confirm delete single record dialog
        recordToDelete?.let { record ->
            ConfirmDeleteDialog(
                onConfirm = {
                    viewModel.deleteRecord(record)
                    recordToDelete = null
                },
                onDismiss = { recordToDelete = null }
            )
        }

        // Confirm revert modified record dialog
        recordToRevert?.let { record ->
            AlertDialog(
                onDismissRequest = { if (!isReverting) recordToRevert = null },
                icon = {
                    Icon(
                        Icons.Filled.Sync,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                },
                title = {
                    Text(
                        text = "استعادة توقيت الوردية من ESS",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "هل تريد استعادة مواعيد الحضور والانصراف الأصلية المسجلة على السيرفر ليوم ${record.workDate}؟",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "• سيتم استرجاع الساعات وأوقات البصمة الحقيقية من السيرفر.\n• سيتم الاحتفاظ بملاحظاتك الحالية دون حذف.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            isReverting = true
                            viewModel.revertRecordToEss(record) { success, msg ->
                                isReverting = false
                                recordToRevert = null
                                viewModel.showHomeMessage(msg)
                            }
                        },
                        enabled = !isReverting
                    ) {
                        if (isReverting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text("استعادة وتحديث")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { recordToRevert = null },
                        enabled = !isReverting
                    ) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}

@Composable
private fun HeroClockCard(
    isClockedIn: Boolean,
    elapsedSeconds: Long,
    checkInTime: Long?,
    onClockToggle: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isClockedIn)
                    ColorWorked.copy(alpha = 0.6f)
                else
                    MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Status Tag
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isClockedIn) ColorWorked.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isClockedIn) ColorWorked.copy(alpha = pulseAlpha)
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isClockedIn) "الوردية جارية الآن" else "خارج أوقات العمل",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isClockedIn) ColorWorked else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Timer display
            val hours = elapsedSeconds / 3600
            val minutes = (elapsedSeconds % 3600) / 60
            val seconds = elapsedSeconds % 60
            val timeText = if (isClockedIn)
                "%02d:%02d:%02d".format(hours, minutes, seconds)
            else
                "00:00:00"

            Text(
                text = timeText,
                style = MaterialTheme.typography.displayMedium.copy(
                    fontSize = 44.sp,
                    letterSpacing = 2.sp
                ),
                fontWeight = FontWeight.Black,
                color = if (isClockedIn) ColorWorked else MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isClockedIn && checkInTime != null) {
                Text(
                    text = "بدأت في: ${PayrollUtil.formatTime(checkInTime)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // High-End Modern Action Trigger Button
            Button(
                onClick = onClockToggle,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isClockedIn) ColorLogout else ColorFingerprint,
                    contentColor = Color.White
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 1.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isClockedIn) Icons.Filled.Logout else Icons.Filled.Fingerprint,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = if (isClockedIn) "تسجيل انصراف" else "تسجيل حضور",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = if (isClockedIn) "اضغط لإنهاء ورديتك الحالية" else "اضغط لبدء احتساب ساعاتك",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactRecordCard(
    record: AttendanceRecord,
    is24Hour: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRevert: (() -> Unit)? = null
) {
    val isOffDay = (record.source == "off_day")
    val isModified = (record.source == "ess_online" && !record.isFromServer)
    val sourceLabel = when {
        isOffDay -> "إجازة"
        isModified -> "معدل"
        record.source == "ess_online" -> "ESS"
        else -> "يدوي"
    }
    val sourceColor = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val isOpen = !isOffDay && record.checkOutTime == null

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(14.dp)
            ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Distinct Attendance Badge (BeachAccess if off_day, Fingerprint if regular)
            Surface(
                shape = CircleShape,
                color = if (isOffDay) ColorOffDay.copy(alpha = 0.15f)
                else ColorFingerprint.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isOffDay) Icons.Filled.BeachAccess else Icons.Filled.Fingerprint,
                        contentDescription = if (isOffDay) "إجازة" else "حضور",
                        tint = if (isOffDay) ColorOffDay else ColorFingerprint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (isOffDay) {
                    Text(
                        text = "يوم إجازة (OFF)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = PayrollUtil.formatTime(record.checkInTime, is24Hour),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = " ← ",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (isOpen) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.HourglassTop,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "مفتوح",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Logout,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = ColorLogout
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = record.checkOutTime?.let { PayrollUtil.formatTime(it, is24Hour) } ?: "",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = sourceColor,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (isModified && onRevert != null) {
                        IconButton(
                            onClick = onRevert,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                Icons.Filled.Sync,
                                contentDescription = "استعادة من ESS",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    if (record.note.isNotBlank() && (!isOffDay || record.note != "إجازة (OFF)")) {
                        Text(
                            text = "• ${record.note}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }

            if (isOffDay) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = ColorOffDay.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "OFF",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ColorOffDay,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            } else if (record.hoursWorked > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = PayrollUtil.formatHours(record.hoursWorked),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            // Single record delete button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "حذف هذا السجل",
                    modifier = Modifier.size(17.dp),
                    tint = ColorDelete
                )
            }
        }
    }
}

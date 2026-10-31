package com.ks.attendance.ui.screens.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.components.StatCard
import com.ks.attendance.ui.theme.*
import com.ks.attendance.utils.PayrollUtil
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(viewModel: MainViewModel) {
    val reportState by viewModel.reportState.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // Date range pickers
    var customStart by remember { mutableStateOf<String?>(null) }
    var customEnd by remember { mutableStateOf<String?>(null) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    // Load report on first compose or when period changes
    LaunchedEffect(customStart, customEnd) {
        viewModel.loadReport(customStart, customEnd)
    }

    // Show export result
    LaunchedEffect(reportState.exportedPath) {
        reportState.exportedPath?.let { path ->
            snackbarHostState.showSnackbar("تم التصدير: $path", duration = SnackbarDuration.Long)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("التقارير", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Period selector ──────────────────────────────────────────────
            item {
                PeriodSelector(
                    currentPeriodLabel = if (customStart != null)
                        "${customStart} → ${customEnd}" else "الفترة الحالية",
                    periodStart = reportState.periodStart,
                    periodEnd = reportState.periodEnd,
                    onStartClick = { showStartPicker = true },
                    onEndClick = { showEndPicker = true },
                    onResetPeriod = {
                        customStart = null
                        customEnd = null
                    }
                )
            }

            // ─── Summary cards ────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "إجمالي الساعات",
                        value = "%.1f".format(reportState.totalHours),
                        subtitle = "ساعة",
                        icon = Icons.Filled.AccessTime,
                        color = MaterialTheme.colorScheme.primaryContainer
                    )
                    StatCard(
                        modifier = Modifier.weight(1f),
                        label = "أيام العمل",
                        value = reportState.workedDays.toString(),
                        subtitle = "يوم",
                        icon = Icons.Filled.CalendarMonth,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    )
                }
            }

            item {
                // Salary card full-width
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.AccountBalance,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("الراتب التقديري",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = formatEGP(reportState.estimatedSalary),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("سعر الساعة",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = "${reportState.hourlyRate.toInt()} ج.م",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // ─── Bar chart ────────────────────────────────────────────────────
            item {
                if (reportState.records.isNotEmpty()) {
                    Text("ساعات العمل اليومية",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    HoursBarChart(records = reportState.records)
                }
            }

            // ─── Action buttons ───────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Export CSV
                    OutlinedButton(
                        onClick = { viewModel.exportCsv() },
                        modifier = Modifier.weight(1f),
                        enabled = !reportState.isExporting && reportState.records.isNotEmpty()
                    ) {
                        if (reportState.isExporting) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Download, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تصدير CSV")
                    }

                    // ESS Sync (only if logged in)
                    if (settingsState.isEssLoggedIn) {
                        Button(
                            onClick = {
                                viewModel.syncFromEss { count ->
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Sync, contentDescription = null,
                                modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("مزامنة ESS")
                        }
                    }
                }
            }

            // ─── Records table ────────────────────────────────────────────────
            if (reportState.records.isNotEmpty()) {
                item {
                    Text("تفاصيل السجلات",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold)
                }
                items(
                    reportState.records.sortedBy { it.checkInTime },
                    key = { it.id }
                ) { record ->
                    RecordTableRow(record = record)
                }
            }
        }
    }

    // Date pickers
    if (showStartPicker) {
        val startState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startState.selectedDateMillis?.let {
                        customStart = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            .format(java.util.Date(it))
                    }
                    showStartPicker = false
                }) { Text("تأكيد") }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) { Text("إلغاء") }
            }
        ) { DatePicker(state = startState) }
    }

    if (showEndPicker) {
        val endState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endState.selectedDateMillis?.let {
                        customEnd = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            .format(java.util.Date(it))
                    }
                    showEndPicker = false
                }) { Text("تأكيد") }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) { Text("إلغاء") }
            }
        ) { DatePicker(state = endState) }
    }
}

@Composable
private fun PeriodSelector(
    currentPeriodLabel: String,
    periodStart: String,
    periodEnd: String,
    onStartClick: () -> Unit,
    onEndClick: () -> Unit,
    onResetPeriod: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("الفترة الزمنية",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f))
                TextButton(onClick = onResetPeriod) {
                    Icon(Icons.Filled.Refresh, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("الحالية")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onStartClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("من: $periodStart", fontSize = 11.sp, textAlign = TextAlign.Center)
                }
                OutlinedButton(
                    onClick = onEndClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("إلى: $periodEnd", fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun HoursBarChart(records: List<AttendanceRecord>) {
    val hoursByDay = records
        .groupBy { it.workDate }
        .mapValues { (_, recs) -> recs.sumOf { it.hoursWorked } }
        .entries
        .sortedBy { it.key }
        .takeLast(14) // Last 14 days max

    if (hoursByDay.isEmpty()) return

    val maxHours = hoursByDay.maxOf { it.value }.coerceAtLeast(1.0)
    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        Canvas(
            modifier = Modifier
                .width((hoursByDay.size * 44).dp)
                .height(140.dp)
        ) {
            val barWidth = size.width / hoursByDay.size
            val chartHeight = size.height - 32.dp.toPx()

            hoursByDay.forEachIndexed { index, (date, hours) ->
                val barHeight = (hours / maxHours * chartHeight).toFloat()
                val left = index * barWidth + barWidth * 0.2f
                val right = (index + 1) * barWidth - barWidth * 0.2f
                val top = chartHeight - barHeight
                val bottom = chartHeight

                // Draw bar
                drawRect(
                    color = primary.copy(alpha = 0.85f),
                    topLeft = Offset(left, top),
                    size = Size(right - left, barHeight)
                )

                // Draw hour label on bar
                if (hours > 0.5) {
                    drawContext.canvas.nativeCanvas.drawText(
                        "%.1f".format(hours),
                        left + (right - left) / 2,
                        top - 4.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = android.graphics.Color.parseColor("#10B981")
                            textSize = 22f
                            textAlign = android.graphics.Paint.Align.CENTER
                            isFakeBoldText = true
                        }
                    )
                }

                // Draw day label
                val dayLabel = date.takeLast(5) // MM-dd
                drawContext.canvas.nativeCanvas.drawText(
                    dayLabel,
                    left + (right - left) / 2,
                    size.height - 2.dp.toPx(),
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.LTGRAY
                        textSize = 18f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                )
            }

            // Baseline
            drawLine(
                color = onSurface.copy(alpha = 0.3f),
                start = Offset(0f, chartHeight),
                end = Offset(size.width, chartHeight),
                strokeWidth = 1.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun RecordTableRow(record: AttendanceRecord) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.width(70.dp)) {
                Text(record.workDate.takeLast(5),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold)
                Text(
                    text = if (record.source == "ess_online") "ESS" else "يدوي",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (record.source == "ess_online")
                        MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.tertiary
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${PayrollUtil.formatTime(record.checkInTime)} → ${
                        record.checkOutTime?.let { PayrollUtil.formatTime(it) } ?: "مفتوح"
                    }",
                    style = MaterialTheme.typography.bodySmall
                )
                if (record.note.isNotBlank()) {
                    Text(
                        text = record.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            Text(
                text = PayrollUtil.formatHoursEn(record.hoursWorked),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun formatEGP(amount: Double): String {
    return "${NumberFormat.getNumberInstance(Locale.US).format(amount.toLong())} ج.م"
}

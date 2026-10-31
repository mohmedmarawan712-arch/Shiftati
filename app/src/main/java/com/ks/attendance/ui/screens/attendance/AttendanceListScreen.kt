package com.ks.attendance.ui.screens.attendance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.components.ConfirmDeleteDialog
import com.ks.attendance.ui.components.EmptyState
import com.ks.attendance.ui.components.KSTopBar
import com.ks.attendance.ui.theme.*
import com.ks.attendance.utils.PayrollUtil
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AttendanceListScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val allRecords by viewModel.allRecords.collectAsState()
    val homeState by viewModel.homeState.collectAsState()
    val settingsState by viewModel.settingsState.collectAsState()
    var recordToDelete by remember { mutableStateOf<AttendanceRecord?>(null) }
    var dayToDelete by remember { mutableStateOf<String?>(null) }
    var recordToRevert by remember { mutableStateOf<AttendanceRecord?>(null) }
    var isReverting by remember { mutableStateOf(false) }

    var filterByPeriod by remember { mutableStateOf(true) }
    val hasActivePeriod = homeState.periodStart.isNotBlank() && homeState.periodEnd.isNotBlank()

    // Filter by active payroll period by default; user can toggle to show all records
    val displayRecords = remember(allRecords, homeState.periodStart, homeState.periodEnd, filterByPeriod, hasActivePeriod) {
        if (!filterByPeriod || !hasActivePeriod) {
            allRecords
        } else {
            allRecords.filter { it.workDate >= homeState.periodStart && it.workDate <= homeState.periodEnd }
        }
    }

    // Group by date descending
    val grouped = remember(displayRecords) {
        displayRecords
            .sortedByDescending { it.workDate }
            .groupBy { it.workDate }
    }

    Scaffold(
        topBar = {
            KSTopBar(
                title = "سجل الحضور",
                onBack = null,
                actions = {
                    IconButton(onClick = { viewModel.exportExcel(onlyCurrentPeriod = filterByPeriod) }) {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = "تصدير كملف Excel (.xls)",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate("record/-1") }) {
                Icon(Icons.Default.Add, contentDescription = "إضافة سجل")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // ─── ملخص النفخ Card ──────────────────────────────────────────────
            item(key = "nafkha_summary") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(22.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ملخص النفخ",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (homeState.periodStart.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = "من ${homeState.periodStart} إلى ${homeState.periodEnd}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Total hours
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "%.1f".format(homeState.monthHours),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "الساعات",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Vertical divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(36.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            )

                            // Worked days
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${homeState.workedDays}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Text(
                                    text = "أيام العمل",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // Vertical divider
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(36.dp)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            )

                            // Estimated Salary
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${NumberFormat.getNumberInstance(Locale.US).format(homeState.monthSalary.toLong())} ج.م",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "الراتب",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ─── Filter Bar ───────────────────────────────────────────────────
            if (hasActivePeriod) {
                item(key = "period_filter_bar") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = filterByPeriod,
                            onClick = { filterByPeriod = !filterByPeriod },
                            label = {
                                Text(
                                    if (filterByPeriod) "سجلات الفترة الحالية (${displayRecords.size})"
                                    else "عرض جميع السجلات (${allRecords.size})",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (filterByPeriod) Icons.Filled.DateRange else Icons.Filled.List,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )

                        Text(
                            text = if (filterByPeriod) "${homeState.periodStart} ~ ${homeState.periodEnd}" else "الكل",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (displayRecords.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            message = if (filterByPeriod && allRecords.isNotEmpty())
                                "لا توجد سجلات في هذه الفترة المحددة\nاضغط على 'عرض جميع السجلات' أو +"
                            else "لا توجد سجلات حضور بعد\nاضغط + لإضافة سجل يدوي",
                            icon = Icons.Default.EventNote
                        )
                    }
                }
            } else {
                grouped.forEach { (date, records) ->
                    // Sticky header
                    stickyHeader(key = "header_$date") {
                        DateGroupHeader(
                            date = date,
                            totalHours = records.sumOf { it.hoursWorked },
                            onDeleteDay = { dayToDelete = date }
                        )
                    }

                    items(records, key = { it.id }) { record ->
                        RecordRow(
                            record = record,
                            is24Hour = settingsState.is24Hour,
                            onDelete = { recordToDelete = record },
                            onEdit = { onNavigate("record/${record.id}") },
                            onRevert = { recordToRevert = record }
                        )
                    }
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
                                color = MaterialTheme.colorScheme.onPrimary
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

        // Confirm delete entire day dialog
        dayToDelete?.let { date ->
            AlertDialog(
                onDismissRequest = { dayToDelete = null },
                title = { Text("حذف يوم العمل بالكامل") },
                text = { Text("هل أنت متأكد من حذف جميع ورديات يوم $date؟\nلن يمكن استرجاع هذه البيانات.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteDay(date)
                            dayToDelete = null
                        },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = ColorDelete
                        )
                    ) {
                        Text("حذف اليوم بالكامل", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { dayToDelete = null }) {
                        Text("إلغاء")
                    }
                }
            )
        }
    }
}

@Composable
private fun DateGroupHeader(
    date: String,
    totalHours: Double,
    onDeleteDay: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val display = remember(date) {
        runCatching {
            SimpleDateFormat("EEEE، d MMMM yyyy", Locale("ar")).format(sdf.parse(date)!!)
        }.getOrDefault(date)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = display,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50.dp),
                    color = ColorWorked.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = PayrollUtil.formatHours(totalHours),
                        style = MaterialTheme.typography.labelMedium,
                        color = ColorWorked,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                IconButton(
                    onClick = onDeleteDay,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "حذف اليوم بالكامل",
                        tint = ColorDelete,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun RecordRow(
    record: AttendanceRecord,
    is24Hour: Boolean = false,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onRevert: (() -> Unit)? = null
) {
    val isOffDay = (record.source == "off_day")
    val checkIn = PayrollUtil.formatTime(record.checkInTime, is24Hour)
    val checkOut = record.checkOutTime?.let { PayrollUtil.formatTime(it, is24Hour) } ?: "مفتوح"
    val isOpen = !isOffDay && record.checkOutTime == null
    val isModified = (record.source == "ess_online" && !record.isFromServer)
    val sourceBadge = when {
        isOffDay -> "إجازة"
        isModified -> "معدل"
        record.source == "ess_online" -> "ESS"
        else -> "يدوي"
    }
    val sourceBadgeColor = when {
        isOffDay -> MaterialTheme.colorScheme.tertiaryContainer
        isModified -> MaterialTheme.colorScheme.tertiaryContainer
        record.source == "ess_online" -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .pointerInput(record.id) {
                detectTapGestures(onLongPress = { onEdit() })
            },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            // Line 1: Check-in, Arrow, Check-out, and Hours Badge (or OFF day banner)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isOffDay) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.BeachAccess,
                            contentDescription = "إجازة",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "يوم إجازة (OFF)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(50.dp),
                        color = ColorOffDay.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "OFF",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = ColorOffDay,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    // Times Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Check-in
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Fingerprint,
                                contentDescription = "حضور",
                                tint = ColorFingerprint,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                text = checkIn,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        Text(
                            text = "←",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Check-out
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = if (isOpen) Icons.Filled.HourglassTop else Icons.Filled.Logout,
                                contentDescription = "انصراف",
                                tint = ColorLogout,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = checkOut,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isOpen) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    // Hours duration chip
                    if (!isOpen) {
                        Surface(
                            shape = RoundedCornerShape(50.dp),
                            color = ColorWorked.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = PayrollUtil.formatHours(record.hoursWorked),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = ColorWorked,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Line 2: Source badge + Note + Action icons (Edit & Delete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left side: Source badge and optional Note
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = sourceBadgeColor
                    ) {
                        Text(
                            text = sourceBadge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Show sync icon button right next to the "معدل" badge
                    if (isModified && onRevert != null) {
                        IconButton(
                            onClick = onRevert,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Sync,
                                contentDescription = "استعادة من ESS",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    if (record.note.isNotBlank()) {
                        Text(
                            text = record.note,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                // Right side: Action buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "تعديل",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "حذف السجل",
                            modifier = Modifier.size(16.dp),
                            tint = ColorDelete
                        )
                    }
                }
            }
        }
    }
}

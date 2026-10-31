package com.ks.attendance.ui.screens.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.data.local.entity.DayNote
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.theme.*
import com.ks.attendance.utils.DateUtil
import com.ks.attendance.utils.PayrollUtil
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: MainViewModel,
    onDayClick: (String) -> Unit
) {
    val allRecords by viewModel.allRecords.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val dayRecords by viewModel.dayRecords.collectAsState()
    val dayNotes by viewModel.dayNotes.collectAsState()

    val today = DateUtil.today()

    // Month navigation state
    val cal = Calendar.getInstance()
    var displayYear by remember { mutableIntStateOf(cal.get(Calendar.YEAR)) }
    var displayMonth by remember { mutableIntStateOf(cal.get(Calendar.MONTH)) } // 0-indexed

    // Derive worked dates from allRecords for current month
    val workedDates by remember(allRecords, displayYear, displayMonth) {
        derivedStateOf {
            allRecords.filter {
                val c = DateUtil.parseToCalendar(it.workDate)
                c.get(Calendar.YEAR) == displayYear && c.get(Calendar.MONTH) == displayMonth
            }.map { it.workDate }.toSet()
        }
    }

    // Dates with notes
    var datesWithNotes by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(displayYear, displayMonth) {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val start = sdf.format(Calendar.getInstance().also { it.set(displayYear, displayMonth, 1) }.time)
        val c = Calendar.getInstance().also {
            it.set(displayYear, displayMonth, 1)
            it.set(Calendar.DAY_OF_MONTH, it.getActualMaximum(Calendar.DAY_OF_MONTH))
        }
        val end = sdf.format(c.time)
        // We'll use allRecords to approximate — actual notes need a query
        datesWithNotes = emptySet()
    }

    // Bottom sheet for selected day
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var showSheet by remember { mutableStateOf(false) }
    var addNoteText by remember { mutableStateOf("") }
    var showNoteInput by remember { mutableStateOf(false) }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false; showNoteInput = false; addNoteText = "" },
            sheetState = sheetState
        ) {
            DayBottomSheet(
                date = selectedDate,
                records = dayRecords,
                notes = dayNotes,
                addNoteText = addNoteText,
                showNoteInput = showNoteInput,
                onAddNoteTextChange = { addNoteText = it },
                onToggleNoteInput = { showNoteInput = !showNoteInput },
                onSaveNote = {
                    if (addNoteText.isNotBlank()) {
                        viewModel.addNote(selectedDate, addNoteText.trim())
                        addNoteText = ""
                        showNoteInput = false
                    }
                },
                onDeleteNote = { viewModel.deleteNote(it) },
                onNavigateAddRecord = {
                    showSheet = false
                    onDayClick(selectedDate)
                }
            )
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Month header ─────────────────────────────────────────────────────
        MonthHeader(
            year = displayYear,
            month = displayMonth,
            onPrev = {
                if (displayMonth == 0) { displayYear--; displayMonth = 11 }
                else displayMonth--
            },
            onNext = {
                if (displayMonth == 11) { displayYear++; displayMonth = 0 }
                else displayMonth++
            }
        )

        // ─── Day-of-week header ───────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Arabic week starts Saturday in Egypt
            listOf("س", "أ", "ن", "ث", "ر", "خ", "ج").forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        HorizontalDivider()

        // ─── Calendar grid ────────────────────────────────────────────────────
        CalendarGrid(
            year = displayYear,
            month = displayMonth,
            today = today,
            selectedDate = selectedDate,
            workedDates = workedDates,
            datesWithNotes = datesWithNotes,
            onDayClick = { date ->
                viewModel.selectDate(date)
                showSheet = true
                showNoteInput = false
                addNoteText = ""
            }
        )
    }
}

@Composable
private fun MonthHeader(year: Int, month: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val arabicMonths = listOf(
        "يناير", "فبراير", "مارس", "إبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "الشهر السابق")
            }
            Text(
                text = "${arabicMonths[month]} $year",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            IconButton(onClick = onNext) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "الشهر التالي")
            }
        }
    }
}

@Composable
private fun CalendarGrid(
    year: Int,
    month: Int,
    today: String,
    selectedDate: String,
    workedDates: Set<String>,
    datesWithNotes: Set<String>,
    onDayClick: (String) -> Unit
) {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // Build the month grid
    val firstDayCal = Calendar.getInstance().also { it.set(year, month, 1) }
    val daysInMonth = firstDayCal.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Offset: 0=Sat, 1=Sun, ... 6=Fri (Egyptian week starting Saturday)
    val firstDayOfWeek = when (firstDayCal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.SATURDAY  -> 0
        Calendar.SUNDAY    -> 1
        Calendar.MONDAY    -> 2
        Calendar.TUESDAY   -> 3
        Calendar.WEDNESDAY -> 4
        Calendar.THURSDAY  -> 5
        Calendar.FRIDAY    -> 6
        else -> 0
    }

    val cells = mutableListOf<String?>()
    repeat(firstDayOfWeek) { cells.add(null) }
    for (day in 1..daysInMonth) {
        firstDayCal.set(Calendar.DAY_OF_MONTH, day)
        cells.add(sdf.format(firstDayCal.time))
    }
    // Pad to complete last row
    while (cells.size % 7 != 0) cells.add(null)

    val rows = cells.chunked(7)

    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        rows.forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { dateStr ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (dateStr != null) {
                            DayCell(
                                dateStr = dateStr,
                                isToday = dateStr == today,
                                isSelected = dateStr == selectedDate,
                                isWorked = dateStr in workedDates,
                                hasNote = dateStr in datesWithNotes,
                                isPast = dateStr < today,
                                onClick = { onDayClick(dateStr) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    dateStr: String,
    isToday: Boolean,
    isSelected: Boolean,
    isWorked: Boolean,
    hasNote: Boolean,
    isPast: Boolean,
    onClick: () -> Unit
) {
    val cal = DateUtil.parseToCalendar(dateStr)
    val dayNum = cal.get(Calendar.DAY_OF_MONTH)

    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday    -> ColorToday.copy(alpha = 0.3f)
        isWorked   -> ColorWorked.copy(alpha = 0.2f)
        isPast     -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
        else       -> Color.Transparent
    }

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday    -> MaterialTheme.colorScheme.onBackground
        isWorked   -> MaterialTheme.colorScheme.primary
        else       -> MaterialTheme.colorScheme.onBackground
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .then(
                if (isToday && !isSelected)
                    Modifier.border(1.5.dp, ColorToday, CircleShape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = dayNum.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
            if (isWorked) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) Color.White else ColorWorked)
                )
            }
            if (hasNote) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(ColorNote)
                )
            }
        }
    }
}

@Composable
private fun DayBottomSheet(
    date: String,
    records: List<AttendanceRecord>,
    notes: List<DayNote>,
    addNoteText: String,
    showNoteInput: Boolean,
    onAddNoteTextChange: (String) -> Unit,
    onToggleNoteInput: () -> Unit,
    onSaveNote: () -> Unit,
    onDeleteNote: (DayNote) -> Unit,
    onNavigateAddRecord: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Date header
        Text(
            text = DateUtil.toArabicFull(date),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))

        // Records section
        Text("سجلات الحضور", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(8.dp))
        if (records.isEmpty()) {
            Text("لا توجد سجلات في هذا اليوم",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            records.forEach { record ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Login, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(PayrollUtil.formatTime(record.checkInTime),
                            style = MaterialTheme.typography.bodyMedium)
                        if (record.checkOutTime != null) {
                            Icon(Icons.Filled.ArrowForward, contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Icon(Icons.Filled.Logout, contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(PayrollUtil.formatTime(record.checkOutTime),
                                style = MaterialTheme.typography.bodyMedium)
                            Spacer(modifier = Modifier.weight(1f))
                            Text(PayrollUtil.formatHours(record.hoursWorked),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary)
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                            Badge { Text("مفتوح") }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onNavigateAddRecord,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("إضافة سجل لهذا اليوم")
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

        // Notes section
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("الملاحظات", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f))
            IconButton(onClick = onToggleNoteInput, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "إضافة ملاحظة",
                    tint = MaterialTheme.colorScheme.primary)
            }
        }

        AnimatedVisibility(visible = showNoteInput, enter = fadeIn(), exit = fadeOut()) {
            Column {
                OutlinedTextField(
                    value = addNoteText,
                    onValueChange = onAddNoteTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("اكتب ملاحظتك هنا...") },
                    minLines = 2
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onToggleNoteInput) { Text("إلغاء") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onSaveNote, enabled = addNoteText.isNotBlank()) {
                        Text("حفظ")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        if (notes.isEmpty() && !showNoteInput) {
            Text("لا توجد ملاحظات", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            notes.forEach { note ->
                Surface(
                    color = ColorNote.copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.StickyNote2, contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(note.noteText, modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium)
                        IconButton(onClick = { onDeleteNote(note) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = null,
                                tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}

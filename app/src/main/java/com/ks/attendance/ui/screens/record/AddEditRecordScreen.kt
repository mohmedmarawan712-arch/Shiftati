package com.ks.attendance.ui.screens.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.components.KSTopBar
import com.ks.attendance.ui.theme.*
import com.ks.attendance.utils.DateUtil
import com.ks.attendance.utils.PayrollUtil
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditRecordScreen(
    viewModel: MainViewModel,
    recordId: Long = -1L,
    initialDate: String? = null,
    onBack: () -> Unit
) {
    val isEdit = recordId != -1L
    val today = DateUtil.today()
    val settingsState by viewModel.settingsState.collectAsState()

    // ─── Local form state ─────────────────────────────────────────────────────
    var workDate by remember { mutableStateOf(initialDate ?: today) }
    var checkInHour by remember { mutableIntStateOf(9) }
    var checkInMinute by remember { mutableIntStateOf(0) }
    var hasCheckOut by remember { mutableStateOf(false) }
    var checkOutHour by remember { mutableIntStateOf(17) }
    var checkOutMinute by remember { mutableIntStateOf(0) }
    var noteText by remember { mutableStateOf("") }
    var isOnlineRecord by remember { mutableStateOf(false) }
    var isOffDay by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Dialogs
    var showDatePicker by remember { mutableStateOf(false) }
    var showCheckInPicker by remember { mutableStateOf(false) }
    var showCheckOutPicker by remember { mutableStateOf(false) }

    // Initial values to check if time/date was actually edited by the user
    var initialWorkDate by remember { mutableStateOf<String?>(null) }
    var initialCheckInHour by remember { mutableIntStateOf(-1) }
    var initialCheckInMinute by remember { mutableIntStateOf(-1) }
    var initialHasCheckOut by remember { mutableStateOf(false) }
    var initialCheckOutHour by remember { mutableIntStateOf(-1) }
    var initialCheckOutMinute by remember { mutableIntStateOf(-1) }
    var initialIsOffDay by remember { mutableStateOf(false) }

    // Load existing record if editing
    LaunchedEffect(recordId) {
        if (isEdit) {
            val record = viewModel.allRecords.value.find { it.id == recordId }
            record?.let { r ->
                workDate = r.workDate
                initialWorkDate = r.workDate
                isOnlineRecord = (r.source == "ess_online")
                isOffDay = (r.source == "off_day")
                initialIsOffDay = (r.source == "off_day")
                val cal = Calendar.getInstance()
                cal.timeInMillis = r.checkInTime
                checkInHour = cal.get(Calendar.HOUR_OF_DAY)
                checkInMinute = cal.get(Calendar.MINUTE)
                initialCheckInHour = checkInHour
                initialCheckInMinute = checkInMinute
                r.checkOutTime?.let { out ->
                    hasCheckOut = true
                    initialHasCheckOut = true
                    cal.timeInMillis = out
                    checkOutHour = cal.get(Calendar.HOUR_OF_DAY)
                    checkOutMinute = cal.get(Calendar.MINUTE)
                    initialCheckOutHour = checkOutHour
                    initialCheckOutMinute = checkOutMinute
                }
                noteText = r.note
            }
        }
    }

    // Overnight shift: when check-out is earlier than check-in (e.g. 7 PM to 5 AM next day)
    val isOvernight = remember(checkInHour, checkInMinute, hasCheckOut, checkOutHour, checkOutMinute) {
        hasCheckOut && (checkOutHour < checkInHour || (checkOutHour == checkInHour && checkOutMinute < checkInMinute))
    }

    // Computed hours
    val computedHours: Double? = remember(
        workDate, checkInHour, checkInMinute, hasCheckOut, checkOutHour, checkOutMinute, isOvernight
    ) {
        if (!hasCheckOut) null
        else if (checkInHour == checkOutHour && checkInMinute == checkOutMinute) null
        else {
            val inMillis = dateTimeToMillis(workDate, checkInHour, checkInMinute)
            val rawOut = dateTimeToMillis(workDate, checkOutHour, checkOutMinute)
            val outMillis = if (isOvernight) rawOut + 86_400_000L else rawOut
            if (outMillis > inMillis) (outMillis - inMillis).toDouble() / 3_600_000.0
            else null
        }
    }

    // Unsaved changes state & dialog
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val hasUnsavedChanges by remember(
        workDate, checkInHour, checkInMinute, hasCheckOut, checkOutHour, checkOutMinute, noteText, isOffDay,
        initialWorkDate, initialCheckInHour, initialCheckInMinute, initialHasCheckOut, initialCheckOutHour, initialCheckOutMinute, initialIsOffDay
    ) {
        derivedStateOf {
            if (!isEdit) {
                isOffDay || noteText.isNotBlank() || workDate != today || checkInHour != 9 || checkInMinute != 0 || hasCheckOut
            } else {
                val existing = viewModel.allRecords.value.find { it.id == recordId }
                (isOffDay != initialIsOffDay) ||
                (initialWorkDate != null && workDate != initialWorkDate) ||
                (!isOffDay && initialCheckInHour != -1 && checkInHour != initialCheckInHour) ||
                (!isOffDay && initialCheckInMinute != -1 && checkInMinute != initialCheckInMinute) ||
                (!isOffDay && hasCheckOut != initialHasCheckOut) ||
                (!isOffDay && hasCheckOut && (checkOutHour != initialCheckOutHour || checkOutMinute != initialCheckOutMinute)) ||
                (noteText.trim() != (existing?.note?.trim() ?: ""))
            }
        }
    }

    fun performSave(): Boolean {
        if (isOffDay) {
            val inMillis = dateTimeToMillis(workDate, 0, 0)
            val defaultNote = if (noteText.isBlank()) "إجازة (OFF)" else noteText.trim()
            val existing = if (isEdit) viewModel.allRecords.value.find { it.id == recordId } else null
            val record = if (isEdit && existing != null) {
                existing.copy(
                    workDate = workDate,
                    checkInTime = inMillis,
                    checkOutTime = inMillis,
                    hoursWorked = 0.0,
                    source = "off_day",
                    note = defaultNote,
                    isFromServer = false
                )
            } else {
                AttendanceRecord(
                    workDate = workDate,
                    checkInTime = inMillis,
                    checkOutTime = inMillis,
                    hoursWorked = 0.0,
                    source = "off_day",
                    note = defaultNote,
                    isFromServer = false
                )
            }
            if (isEdit) viewModel.updateRecord(record) else viewModel.addRecord(record)
            return true
        }

        val timeOrDateModifiedByUser = (workDate != initialWorkDate) ||
            (checkInHour != initialCheckInHour) ||
            (checkInMinute != initialCheckInMinute) ||
            (hasCheckOut != initialHasCheckOut) ||
            (hasCheckOut && (checkOutHour != initialCheckOutHour || checkOutMinute != initialCheckOutMinute))

        val existing = if (isEdit) viewModel.allRecords.value.find { it.id == recordId } else null

        val inMillis = if (isEdit && !timeOrDateModifiedByUser && existing != null) {
            existing.checkInTime
        } else {
            dateTimeToMillis(workDate, checkInHour, checkInMinute)
        }

        val outMillis = if (!hasCheckOut) {
            null
        } else if (isEdit && !timeOrDateModifiedByUser && existing != null) {
            existing.checkOutTime
        } else {
            val rawOut = dateTimeToMillis(workDate, checkOutHour, checkOutMinute)
            if (isOvernight) rawOut + 86_400_000L else rawOut
        }

        if (outMillis != null && outMillis == inMillis) {
            errorMsg = "وقت الانصراف لا يمكن أن يكون نفس وقت الدخول"
            return false
        }
        if (outMillis != null && outMillis < inMillis) {
            errorMsg = "وقت الانصراف يجب أن يكون بعد وقت الدخول"
            return false
        }
        errorMsg = null

        val hours = if (outMillis != null) {
            if (isEdit && !timeOrDateModifiedByUser && existing != null) {
                existing.hoursWorked
            } else {
                (outMillis - inMillis).toDouble() / 3_600_000.0
            }
        } else 0.0

        val record = if (isEdit && existing != null) {
            existing.copy(
                workDate = workDate,
                checkInTime = inMillis,
                checkOutTime = outMillis,
                hoursWorked = hours,
                note = noteText.trim(),
                source = if (existing.source == "off_day") "manual" else existing.source,
                isFromServer = if (timeOrDateModifiedByUser) false else existing.isFromServer
            )
        } else {
            AttendanceRecord(
                workDate = workDate,
                checkInTime = inMillis,
                checkOutTime = outMillis,
                hoursWorked = hours,
                note = noteText.trim(),
                source = "manual"
            )
        }
        if (isEdit) viewModel.updateRecord(record) else viewModel.addRecord(record)
        return true
    }

    val handleBack = {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onBack()
        }
    }

    BackHandler(enabled = hasUnsavedChanges) {
        showUnsavedDialog = true
    }

    Scaffold(
        topBar = {
            KSTopBar(
                title = if (isEdit) "تعديل السجل" else "إضافة سجل",
                onBack = handleBack,
                actions = {
                    IconButton(
                        onClick = {
                            if (performSave()) onBack()
                        }
                    ) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "حفظ",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            )
        }
    ) { padding ->
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            if (isOnlineRecord) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "وردية مستوردة من ESS (تعديلك يُحفظ محلياً)",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // ─── Date ─────────────────────────────────────────────────────────
            SectionLabel("التاريخ")
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = DateUtil.toArabicFull(workDate),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = workDate,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Filled.ChevronRight, contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ─── OFF Day Toggle Card ──────────────────────────────────────────
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isOffDay = !isOffDay },
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = if (isOffDay) ColorOffDay.copy(alpha = 0.12f)
                    else MaterialTheme.colorScheme.surface
                ),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (isOffDay) ColorOffDay.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.BeachAccess,
                        contentDescription = "يوم إجازة",
                        tint = if (isOffDay) ColorOffDay else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "يوم إجازة (OFF)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isOffDay) ColorOffDay else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isOffDay) "عطلة رسمية / إجازة بدون ساعات عمل وتوقيت" else "تحديد هذا اليوم كإجازة بدون مواعيد حضور وانصراف",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isOffDay,
                        onCheckedChange = { isOffDay = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = androidx.compose.ui.graphics.Color.White,
                            checkedTrackColor = ColorOffDay
                        )
                    )
                }
            }

            if (!isOffDay) {
                // ─── Check-in time ────────────────────────────────────────────────
                SectionLabel("وقت الدخول")
                TimePickerRow(
                    hour = checkInHour,
                    minute = checkInMinute,
                    label = "وقت الدخول",
                    icon = Icons.Filled.Fingerprint,
                    iconTint = ColorFingerprint,
                    is24Hour = settingsState.is24Hour,
                    onClick = { showCheckInPicker = true }
                )

                // ─── Check-out time ───────────────────────────────────────────────
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SectionLabel("وقت الانصراف", modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("اختياري", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(checked = hasCheckOut, onCheckedChange = { hasCheckOut = it })
                    }
                }
                if (hasCheckOut) {
                    TimePickerRow(
                        hour = checkOutHour,
                        minute = checkOutMinute,
                        label = "وقت الانصراف",
                        icon = Icons.Filled.Logout,
                        iconTint = ColorLogout,
                        is24Hour = settingsState.is24Hour,
                        onClick = { showCheckOutPicker = true }
                    )
                }

                // ─── Computed hours ───────────────────────────────────────────────
                computedHours?.let { hours ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(Icons.Filled.Timer, contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "الساعات المحسوبة: ${PayrollUtil.formatHours(hours)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            if (isOvernight) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer
                                ) {
                                    Text(
                                        text = "شفت ليلي (ينتهي غداً)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.BeachAccess,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "تم تسجيل اليوم كإجازة (OFF). لا يلزم إدخال مواعيد.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // ─── Note ─────────────────────────────────────────────────────────
            SectionLabel("ملاحظة (اختياري)")
            OutlinedTextField(
                value = noteText,
                onValueChange = { noteText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("أضف ملاحظة عن هذا اليوم أو الوردية...") },
                minLines = 3,
                maxLines = 5,
                leadingIcon = { Icon(Icons.Filled.StickyNote2, contentDescription = null) }
            )

            // ─── Error ────────────────────────────────────────────────────────
            errorMsg?.let {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Error, contentDescription = null,
                            tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ─── Unsaved Changes Dialog ───────────────────────────────────────────────
    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            icon = {
                Icon(
                    Icons.Filled.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = {
                Text(
                    text = "تغييرات غير محفوظة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "هل تريد حفظ التعديلات قبل الخروج أم تجاهلها؟",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUnsavedDialog = false
                        if (performSave()) onBack()
                    }
                ) {
                    Text("حفظ التغييرات")
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            showUnsavedDialog = false
                            onBack()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("تجاهل")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { showUnsavedDialog = false }) {
                        Text("إلغاء")
                    }
                }
            }
        )
    }

    // ─── Dialogs ──────────────────────────────────────────────────────────────
    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = DateUtil.parseToCalendar(workDate).timeInMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        workDate = DateUtil.formatDate(it)
                    }
                    showDatePicker = false
                }) { Text("تأكيد") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("إلغاء") }
            }
        ) { DatePicker(state = state) }
    }

    if (showCheckInPicker) {
        TimePickerDialog(
            initialHour = checkInHour,
            initialMinute = checkInMinute,
            onConfirm = { h, m -> checkInHour = h; checkInMinute = m; showCheckInPicker = false },
            onDismiss = { showCheckInPicker = false }
        )
    }

    if (showCheckOutPicker) {
        TimePickerDialog(
            initialHour = checkOutHour,
            initialMinute = checkOutMinute,
            onConfirm = { h, m -> checkOutHour = h; checkOutMinute = m; showCheckOutPicker = false },
            onDismiss = { showCheckOutPicker = false }
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
    )
}

@Composable
private fun TimePickerRow(
    hour: Int,
    minute: Int,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    is24Hour: Boolean = false,
    onClick: () -> Unit
) {
    val timeDisplay = if (is24Hour) {
        "%02d:%02d".format(hour, minute)
    } else {
        val h = if (hour % 12 == 0) 12 else hour % 12
        val amPm = if (hour < 12) "ص" else "م"
        "$h:%02d $amPm".format(minute)
    }

    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = iconTint)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = timeDisplay,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(label, style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر الوقت") },
        text = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TimePicker(state = state)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("تأكيد") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

private fun dateTimeToMillis(date: String, hour: Int, minute: Int): Long {
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    return sdf.parse("$date %02d:%02d".format(hour, minute))?.time ?: System.currentTimeMillis()
}

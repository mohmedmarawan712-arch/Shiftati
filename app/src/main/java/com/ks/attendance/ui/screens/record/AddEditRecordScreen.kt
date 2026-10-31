package com.ks.attendance.ui.screens.record

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
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Dialogs
    var showDatePicker by remember { mutableStateOf(false) }
    var showCheckInPicker by remember { mutableStateOf(false) }
    var showCheckOutPicker by remember { mutableStateOf(false) }

    // Load existing record if editing
    LaunchedEffect(recordId) {
        if (isEdit) {
            val record = viewModel.allRecords.value.find { it.id == recordId }
            record?.let { r ->
                workDate = r.workDate
                isOnlineRecord = (r.source == "ess_online")
                val cal = Calendar.getInstance()
                cal.timeInMillis = r.checkInTime
                checkInHour = cal.get(Calendar.HOUR_OF_DAY)
                checkInMinute = cal.get(Calendar.MINUTE)
                r.checkOutTime?.let { out ->
                    hasCheckOut = true
                    cal.timeInMillis = out
                    checkOutHour = cal.get(Calendar.HOUR_OF_DAY)
                    checkOutMinute = cal.get(Calendar.MINUTE)
                }
                noteText = r.note
            }
        }
    }

    // Computed hours
    val computedHours: Double? = remember(
        workDate, checkInHour, checkInMinute, hasCheckOut, checkOutHour, checkOutMinute
    ) {
        if (!hasCheckOut) null
        else {
            val inMillis = dateTimeToMillis(workDate, checkInHour, checkInMinute)
            val outMillis = dateTimeToMillis(workDate, checkOutHour, checkOutMinute)
            if (outMillis > inMillis) (outMillis - inMillis).toDouble() / 3_600_000.0
            else null
        }
    }

    Scaffold(
        topBar = {
            KSTopBar(
                title = if (isEdit) "تعديل السجل" else "إضافة سجل",
                onBack = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
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
                            text = "هذه الوردية مستوردة من نظام ESS. يمكنك تعديل الساعات أو التاريخ بحرية، وسيتم تثبيت تعديلاتك محلياً ولن تتأثر بأي مزامنة لاحقة.",
                            style = MaterialTheme.typography.bodySmall,
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

            // ─── Check-in time ────────────────────────────────────────────────
            SectionLabel("وقت الدخول")
            TimePickerRow(
                hour = checkInHour,
                minute = checkInMinute,
                label = "وقت الدخول",
                icon = Icons.Filled.Login,
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
                        verticalAlignment = Alignment.CenterVertically
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

            Spacer(modifier = Modifier.height(8.dp))

            // ─── Save button ──────────────────────────────────────────────────
            Button(
                onClick = {
                    val inMillis = dateTimeToMillis(workDate, checkInHour, checkInMinute)
                    val outMillis = if (hasCheckOut)
                        dateTimeToMillis(workDate, checkOutHour, checkOutMinute)
                    else null

                    if (outMillis != null && outMillis <= inMillis) {
                        errorMsg = "وقت الانصراف يجب أن يكون بعد وقت الدخول"
                        return@Button
                    }
                    errorMsg = null
                    val hours = if (outMillis != null) (outMillis - inMillis).toDouble() / 3_600_000.0 else 0.0
                    val record = if (isEdit) {
                        val existing = viewModel.allRecords.value.find { it.id == recordId }!!
                        existing.copy(
                            workDate = workDate,
                            checkInTime = inMillis,
                            checkOutTime = outMillis,
                            hoursWorked = hours,
                            note = noteText.trim()
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
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("حفظ", style = MaterialTheme.typography.titleMedium)
            }
        }
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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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

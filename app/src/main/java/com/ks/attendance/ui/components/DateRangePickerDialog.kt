package com.ks.attendance.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ks.attendance.utils.DateUtil
import com.ks.attendance.utils.PayrollUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangePickerDialog(
    initialStartDate: String = "",
    initialEndDate: String = "",
    onConfirm: (startDate: String, endDate: String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialStartMillis = remember(initialStartDate) {
        if (initialStartDate.isNotBlank()) DateUtil.dateToUtcMillis(initialStartDate) else null
    }
    val initialEndMillis = remember(initialEndDate) {
        if (initialEndDate.isNotBlank()) DateUtil.dateToUtcMillis(initialEndDate) else null
    }

    val state = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis,
        initialSelectedEndDateMillis = initialEndMillis
    )

    val selectedStartStr = state.selectedStartDateMillis?.let { DateUtil.utcMillisToDate(it) } ?: ""
    val selectedEndStr = state.selectedEndDateMillis?.let { DateUtil.utcMillisToDate(it) } ?: ""
    val daysCount = if (selectedStartStr.isNotBlank() && selectedEndStr.isNotBlank()) {
        DateUtil.calculateDaysCount(selectedStartStr, selectedEndStr)
    } else 0

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    if (selectedStartStr.isNotBlank() && selectedEndStr.isNotBlank()) {
                        val (s, e) = if (selectedStartStr > selectedEndStr) {
                            selectedEndStr to selectedStartStr
                        } else {
                            selectedStartStr to selectedEndStr
                        }
                        onConfirm(s, e)
                    }
                },
                enabled = selectedStartStr.isNotBlank() && selectedEndStr.isNotBlank(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bolt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("تطبيق وجلب البيانات ⚡", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("إلغاء")
            }
        },
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            // Header: Title & Selected Days Badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "اختر الفترة المخصصة",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (daysCount > 0) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "$daysCount يوم",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Quick Presets Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val presets: List<Pair<String, () -> Pair<String, String>>> = listOf(
                    "الشهر الحالي" to { PayrollUtil.getCurrentMonthPeriod() },
                    "الشهر السابق" to { PayrollUtil.getPreviousMonthPeriod() },
                    "دورة 16 - 15" to { PayrollUtil.getPayrollPeriod16to15() },
                    "دورة 29 - 28" to { PayrollUtil.getPayrollPeriod29to28() },
                    "آخر 30 يوم" to { DateUtil.getLast30DaysPeriod() },
                    "آخر 14 يوم" to { DateUtil.getLast14DaysPeriod() },
                    "آخر 7 أيام" to { DateUtil.getLast7DaysPeriod() }
                )
                items(presets) { (title, provider) ->
                    val (pStart, pEnd) = provider()
                    val isSelected = selectedStartStr == pStart && selectedEndStr == pEnd
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            val sUtc = DateUtil.dateToUtcMillis(pStart)
                            val eUtc = DateUtil.dateToUtcMillis(pEnd)
                            state.setSelection(sUtc, eUtc)
                        },
                        label = { Text(title, style = MaterialTheme.typography.labelSmall) },
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // The DateRangePicker
            DateRangePicker(
                state = state,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                showModeToggle = false,
                title = null,
                headline = null
            )
        }
    }
}

package com.ks.attendance.ui.screens.attendance

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.components.ConfirmDeleteDialog
import com.ks.attendance.ui.components.EmptyState
import com.ks.attendance.ui.components.KSTopBar
import com.ks.attendance.ui.theme.ColorWorked
import com.ks.attendance.utils.PayrollUtil
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AttendanceListScreen(
    viewModel: MainViewModel,
    onNavigate: (String) -> Unit
) {
    val allRecords by viewModel.allRecords.collectAsState()
    var recordToDelete by remember { mutableStateOf<AttendanceRecord?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Group by date descending
    val grouped = remember(allRecords) {
        allRecords
            .sortedByDescending { it.workDate }
            .groupBy { it.workDate }
    }

    Scaffold(
        topBar = {
            KSTopBar(
                title = "سجل الحضور",
                onBack = { onNavigate("home") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onNavigate("record/-1") }) {
                Icon(Icons.Default.Add, contentDescription = "إضافة سجل")
            }
        }
    ) { padding ->
        if (allRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    message = "لا توجد سجلات حضور بعد\nاضغط + لإضافة سجل يدوي",
                    icon = Icons.Default.EventNote
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                grouped.forEach { (date, records) ->
                    // Sticky header
                    stickyHeader(key = "header_$date") {
                        DateGroupHeader(
                            date = date,
                            totalHours = records.sumOf { it.hoursWorked }
                        )
                    }

                    items(records, key = { it.id }) { record ->
                        SwipeToDismissRecordItem(
                            record = record,
                            onDelete = { recordToDelete = record },
                            onEdit = { onNavigate("record/${record.id}") }
                        )
                    }
                }
            }
        }

        // Confirm delete dialog
        recordToDelete?.let { record ->
            ConfirmDeleteDialog(
                onConfirm = {
                    viewModel.deleteRecord(record)
                    recordToDelete = null
                },
                onDismiss = { recordToDelete = null }
            )
        }
    }
}

@Composable
private fun DateGroupHeader(date: String, totalHours: Double) {
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
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = display,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
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
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDismissRecordItem(
    record: AttendanceRecord,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }
            false // don't auto-dismiss; dialog handles deletion
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            val color by animateColorAsState(
                targetValue = if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart)
                    MaterialTheme.colorScheme.errorContainer
                else Color.Transparent,
                label = "swipeBg"
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color)
                    .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "حذف",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) {
        RecordRow(
            record = record,
            onEdit = onEdit
        )
    }
}

@Composable
private fun RecordRow(
    record: AttendanceRecord,
    onEdit: () -> Unit
) {
    val checkIn = PayrollUtil.formatTime(record.checkInTime)
    val checkOut = record.checkOutTime?.let { PayrollUtil.formatTime(it) } ?: "مفتوح"
    val isOpen = record.checkOutTime == null
    val sourceBadge = if (record.source == "ess_online") "ESS" else "يدوي"
    val sourceBadgeColor = if (record.source == "ess_online")
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.secondaryContainer

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(record.id) {
                detectTapGestures(onLongPress = { onEdit() })
            },
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Times column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Default.Login,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = checkIn,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(" — ", style = MaterialTheme.typography.bodyMedium)
                    Icon(
                        Icons.Default.Logout,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = if (isOpen) MaterialTheme.colorScheme.error
                               else MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        text = checkOut,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (isOpen) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurface
                    )
                }
                if (record.note.isNotBlank()) {
                    Text(
                        text = record.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }

            // Hours badge
            if (!isOpen) {
                Text(
                    text = PayrollUtil.formatHours(record.hoursWorked),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Source badge
            Surface(
                shape = RoundedCornerShape(50.dp),
                color = sourceBadgeColor
            ) {
                Text(
                    text = sourceBadge,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            // Edit icon
            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "تعديل",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}

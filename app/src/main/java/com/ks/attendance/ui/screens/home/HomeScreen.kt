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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ks.attendance.R
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.components.ConfirmDeleteDialog
import com.ks.attendance.ui.components.DateRangePickerDialog
import com.ks.attendance.ui.components.EmptyState
import com.ks.attendance.ui.components.KSTopBar
import com.ks.attendance.ui.theme.*
import com.ks.attendance.utils.DateUtil
import com.ks.attendance.utils.PayrollUtil
import com.ks.attendance.utils.qr.QrUtil
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
    var recordToDelete by remember { mutableStateOf<AttendanceRecord?>(null) }
    var recordToRevert by remember { mutableStateOf<AttendanceRecord?>(null) }
    var isReverting by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(homeState.message) {
        homeState.message?.let { msg ->
            android.widget.Toast.makeText(context, msg.text, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    // Attendance QR & Account Manager states
    var showQrDialog by remember { mutableStateOf(false) }
    var qrInitialEvent by remember { mutableStateOf(QrUtil.EVENT_CLOCK_IN) }
    var showAccountsManagerDialog by remember { mutableStateOf(false) }
    var showCustomRangePicker by remember { mutableStateOf(false) }

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

            // ─── ESS Account Selector & Status Bar ────────────────────────────
            item {
                AccountCardHeaderBar(
                    activeAccount = homeState.activeAccount,
                    accountsCount = homeState.allAccounts.size,
                    onOpenAccountManager = { showAccountsManagerDialog = true },
                    onQuickAddAccount = { onNavigate("settings") }
                )
            }

            // ─── Offline QR Attendance Action Buttons (دخول / خروج) ─────────────
            item {
                AttendanceQrQuickActionButtons(
                    onClockInQrClick = {
                        qrInitialEvent = QrUtil.EVENT_CLOCK_IN
                        if (homeState.activeAccount != null) {
                            showQrDialog = true
                        } else {
                            viewModel.showHomeMessage(context.getString(R.string.attendance_require_account), isError = true)
                            onNavigate("settings")
                        }
                    },
                    onClockOutQrClick = {
                        qrInitialEvent = QrUtil.EVENT_CLOCK_OUT
                        if (homeState.activeAccount != null) {
                            showQrDialog = true
                        } else {
                            viewModel.showHomeMessage(context.getString(R.string.attendance_require_account), isError = true)
                            onNavigate("settings")
                        }
                    }
                )
            }

            // ─── Pending Incomplete Shifts Alert Card (حضور بدون انصراف - عطل ESS) ─────────────
            if (homeState.pendingMissingCheckouts.isNotEmpty()) {
                item {
                    val pendingList = homeState.pendingMissingCheckouts
                    val firstPending = pendingList.first()
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF59E0B).copy(alpha = 0.12f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.5.dp,
                                color = Color(0xFFF59E0B).copy(alpha = 0.65f),
                                shape = RoundedCornerShape(18.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = Color(0xFFF59E0B).copy(alpha = 0.2f),
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.WarningAmber,
                                                contentDescription = null,
                                                tint = Color(0xFFF59E0B),
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = if (pendingList.size == 1) "وردية بدون انصراف (سيرفر ESS) ⚠️"
                                                   else "${pendingList.size} ورديات بدون انصراف ⚠️",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "بسبب سقوط سيستم ESS لم يُسجل الانصراف",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFF59E0B).copy(alpha = 0.25f)
                                ) {
                                    Text(
                                        text = "تحتاج تسوية",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD97706),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFF59E0B).copy(alpha = 0.25f))
                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "يوم ${firstPending.workDate} • حضور الساعة ${PayrollUtil.formatTime(firstPending.checkInTime, homeState.is24Hour)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "الساعات محسوبة حالياً 0 دقيقة. يمكنك تسوية الوردية لاحتساب ساعاتها وراتبها فوراً.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Button(
                                    onClick = {
                                        if (pendingList.size == 1) {
                                            viewModel.settleMissingCheckout(firstPending)
                                        } else {
                                            viewModel.settleAllMissingCheckouts()
                                        }
                                    },
                                    modifier = Modifier.weight(1.2f),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF59E0B),
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (pendingList.size == 1) "تسوية شفت كامل (%.0f س)".format(homeState.standardShiftHours)
                                               else "تسوية الكل (%.0f س)".format(homeState.standardShiftHours),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                OutlinedButton(
                                    onClick = { onNavigate("record/${firstPending.id}") },
                                    modifier = Modifier.weight(0.9f),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.6f))
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp), tint = Color(0xFFF59E0B))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "تعديل الوقت",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Active Attendance / Live Session Card ────────────────────────
            if (homeState.openSession != null) {
                item {
                    val openSession = homeState.openSession ?: return@item
                    var currentTime by remember { mutableStateOf(System.currentTimeMillis()) }
                    LaunchedEffect(openSession.checkInTime) {
                        while (true) {
                            currentTime = System.currentTimeMillis()
                            delay(30_000L)
                        }
                    }

                    val elapsedMillis = maxOf(0L, currentTime - openSession.checkInTime)
                    val elapsedHours = elapsedMillis / 3_600_000L
                    val elapsedMins = (elapsedMillis % 3_600_000L) / 60_000L
                    val checkInFormatted = PayrollUtil.formatTime(openSession.checkInTime, homeState.is24Hour)

                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                width = 1.5.dp,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(18.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Top Row: Status badge & Live indicator
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = CircleShape,
                                        color = ColorWorked.copy(alpha = 0.2f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                imageVector = Icons.Filled.Fingerprint,
                                                contentDescription = null,
                                                tint = ColorWorked,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(ColorWorked)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "أنت مسجل حضور الآن 🟢",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = if (openSession.isFromServer) "مصدر البصمة: جهاز ESS السيرفر" else "مصدر البصمة: تسجيل بالـ QR",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = "دوام جاري ⏳",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorWorked,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(14.dp))

                            // Time & Duration details
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "وقت تسجيل الحضور",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = checkInFormatted,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = openSession.workDate,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "المدة المستمرة حتى الآن",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (elapsedHours > 0) "${elapsedHours} س و ${elapsedMins} د" else "${elapsedMins} دقيقة",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action button: Quick Clock-Out QR
                            Button(
                                onClick = {
                                    qrInitialEvent = QrUtil.EVENT_CLOCK_OUT
                                    if (homeState.activeAccount != null) {
                                        showQrDialog = true
                                    } else {
                                        viewModel.showHomeMessage(context.getString(R.string.attendance_require_account), isError = true)
                                        onNavigate("settings")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = ColorLogout)
                            ) {
                                Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("تسجيل الانصراف (QR)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ─── Period Summary Card ──────────────────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            shape = RoundedCornerShape(16.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (homeState.isFixedEmployment) Icons.Filled.Badge else Icons.Filled.Schedule,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (homeState.isFixedEmployment) "ملخص الشهر (تعيين)" else "ملخص الشهر (ساعة)",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (homeState.periodStart.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.clickable { showCustomRangePicker = true }
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DateRange,
                                            contentDescription = "تغيير الفترة",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${homeState.periodStart} → ${homeState.periodEnd}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (homeState.isFixedEmployment) {
                                // 1. Base regular hours counted
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "%.0f س".format(homeState.regularHours),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "ساعات أساسية",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )

                                // 2. Overtime hours
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "+%.1f س".format(homeState.overtimeHours),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "ساعات إضافية",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )

                                // 3. Total overtime compensation (only overtime price!)
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${NumberFormat.getNumberInstance(Locale.US).format(homeState.overtimeSalary.toLong())} ج",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "مستحق الإضافي",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                // Hourly mode: Total raw hours, Net hours after break deduction, and Salary
                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "%.1f س".format(homeState.rawMonthHours),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "الساعات بالكامل",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "%.1f س".format(homeState.netMonthHours),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "بعد الخصم (-0.5س)",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .width(1.dp)
                                        .height(28.dp)
                                        .background(MaterialTheme.colorScheme.outlineVariant)
                                )

                                Column(
                                    modifier = Modifier.weight(1f),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "${NumberFormat.getNumberInstance(Locale.US).format(homeState.monthSalary.toLong())} ج",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "الراتب (${homeState.workedDays} يوم)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
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
                    if (homeState.todayRecords.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = "${homeState.todayRecords.size} وردية",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
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
                        isFixedEmployment = homeState.isFixedEmployment,
                        hourlyRate = homeState.hourlyRate,
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
                    Text(
                        text = "استعادة توقيت البصمة الأصلي من السيرفر ليوم ${record.workDate}؟",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
                        Text("استعادة")
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

        // ─── First-Launch Employment Type Selection Dialog ────────────────────
        if (!homeState.hasChosenEmployment) {
            EmploymentTypeDialog(
                initialIsFixed = homeState.isFixedEmployment,
                onConfirm = { isFixed ->
                    viewModel.selectInitialEmploymentType(isFixed)
                }
            )
        }

        // ─── Attendance QR Code Dialog ────────────────────────────────────────
        if (showQrDialog && homeState.activeAccount != null) {
            AttendanceQrDialog(
                account = homeState.activeAccount!!,
                allAccounts = homeState.allAccounts,
                initialEvent = qrInitialEvent,
                onDismiss = { showQrDialog = false },
                onSwitchAccount = { viewModel.switchAccount(it) },
                onGeneratePayload = { acc, event, date -> viewModel.generateQrPayload(acc, event, date) },
                onGenerateBitmap = { payload -> viewModel.generateQrBitmap(payload) },
                onConfirmAttendance = { isClockIn, time ->
                    if (isClockIn) {
                        viewModel.clockIn(time.time)
                    } else {
                        viewModel.clockOut(time.time)
                    }
                }
            )
        }

        // ─── Accounts Manager Dialog ──────────────────────────────────────────
        if (showAccountsManagerDialog) {
            AccountsManagerDialog(
                accounts = homeState.allAccounts,
                activeAccount = homeState.activeAccount,
                onDismiss = { showAccountsManagerDialog = false },
                onSelectAccount = { acc ->
                    viewModel.switchAccount(acc)
                    showAccountsManagerDialog = false
                },
                onDeleteAccount = { acc -> viewModel.deleteAccount(acc) },
                onRefreshActive = { viewModel.refreshActiveAccount() },
                onOpenAddAccount = {
                    showAccountsManagerDialog = false
                    onNavigate("settings")
                }
            )
        }

        // ─── Custom Date Range Picker Dialog ──────────────────────────────────
        if (showCustomRangePicker) {
            DateRangePickerDialog(
                initialStartDate = homeState.periodStart,
                initialEndDate = homeState.periodEnd,
                onConfirm = { start, end ->
                    showCustomRangePicker = false
                    viewModel.setCustomPeriodRange(start, end, autoSync = true)
                },
                onDismiss = { showCustomRangePicker = false }
            )
        }
    }
}

@Composable
private fun CompactRecordCard(
    record: AttendanceRecord,
    is24Hour: Boolean = false,
    isFixedEmployment: Boolean = false,
    hourlyRate: Double = 30.0,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRevert: (() -> Unit)? = null
) {
    val isOffDay = (record.source == "off_day")
    val isModified = (record.source == "ess_online" && !record.isFromServer)
    val sourceLabel = when {
        isOffDay -> "يدوي"
        isModified -> "معدل"
        record.source == "ess_online" -> "ESS"
        else -> "يدوي"
    }
    val sourceColor = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val isOpen = !isOffDay && record.checkOutTime == null
    val isMissingCheckout = isOpen && (System.currentTimeMillis() - record.checkInTime >= 16 * 3600 * 1000L || record.workDate < DateUtil.today())

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isMissingCheckout) Color(0xFFF59E0B).copy(alpha = 0.06f)
            else MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isMissingCheckout) 1.5.dp else 1.dp,
                color = if (isMissingCheckout) Color(0xFFF59E0B).copy(alpha = 0.7f)
                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
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
            // Distinct Attendance Badge (BeachAccess if off_day, Fingerprint if regular, Warning if missing checkout)
            Surface(
                shape = CircleShape,
                color = if (isOffDay) ColorOffDay.copy(alpha = 0.15f)
                else if (isMissingCheckout) Color(0xFFF59E0B).copy(alpha = 0.15f)
                else ColorFingerprint.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isOffDay) Icons.Filled.BeachAccess
                        else if (isMissingCheckout) Icons.Filled.WarningAmber
                        else Icons.Filled.Fingerprint,
                        contentDescription = if (isOffDay) "إجازة" else "حضور",
                        tint = if (isOffDay) ColorOffDay
                        else if (isMissingCheckout) Color(0xFFF59E0B)
                        else ColorFingerprint,
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
                        if (isMissingCheckout) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.WarningAmber,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = Color(0xFFF59E0B)
                                )
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = "انصراف مفقود",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFF59E0B)
                                )
                            }
                        } else if (isOpen) {
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

                    // Display overtime chip cleanly on Line 2 without expanding right column or wrapping
                    if (isFixedEmployment && record.hoursWorked > 9.0) {
                        val ot = record.hoursWorked - 9.0
                        val otMoney = ot * hourlyRate
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ) {
                            Text(
                                text = "+%.1f س إضافي (+%.0f ج)".format(ot, otMoney),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else if (!isFixedEmployment && record.hoursWorked > 0.5 && !isOffDay) {
                        val net = record.hoursWorked - 0.5
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "صافي: %.1f س (-0.5 بريك)".format(net),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
                        color = MaterialTheme.colorScheme.onSurface,
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

@Composable
fun EmploymentTypeDialog(
    initialIsFixed: Boolean = false,
    onConfirm: (isFixed: Boolean) -> Unit
) {
    var selectedIsFixed by remember { mutableStateOf(initialIsFixed) }

    AlertDialog(
        onDismissRequest = { /* Cannot dismiss without choosing */ },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.WorkOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.section_employment_type),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.employment_dialog_title),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Option 1: Hourly (نظام الساعة) - Placed first as requested
                Surface(
                    onClick = { selectedIsFixed = false },
                    shape = RoundedCornerShape(16.dp),
                    color = if (!selectedIsFixed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (!selectedIsFixed) 2.dp else 1.dp,
                        color = if (!selectedIsFixed) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !selectedIsFixed,
                            onClick = { selectedIsFixed = false },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.employment_hourly_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.employment_hourly_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 2: Fixed employment (نظام التعيين)
                Surface(
                    onClick = { selectedIsFixed = true },
                    shape = RoundedCornerShape(16.dp),
                    color = if (selectedIsFixed) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    border = androidx.compose.foundation.BorderStroke(
                        width = if (selectedIsFixed) 2.dp else 1.dp,
                        color = if (selectedIsFixed) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedIsFixed,
                            onClick = { selectedIsFixed = true },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.employment_salaried_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.employment_salaried_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selectedIsFixed) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = stringResource(R.string.employment_confirm_choice),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}



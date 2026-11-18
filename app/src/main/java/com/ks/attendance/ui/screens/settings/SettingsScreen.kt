package com.ks.attendance.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ks.attendance.R
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.components.DatePickerDialog
import com.ks.attendance.ui.components.DateRangePickerDialog
import com.ks.attendance.ui.components.KSTopBar
import com.ks.attendance.ui.screens.home.AccountCardHeaderBar
import com.ks.attendance.ui.screens.home.AccountsManagerDialog
import com.ks.attendance.ui.screens.home.AddAccountDialog
import com.ks.attendance.ui.theme.*
import com.ks.attendance.utils.DateUtil
import com.ks.attendance.utils.PayrollUtil
import com.ks.attendance.utils.PrefsUtil
import androidx.compose.ui.graphics.Color
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: (() -> Unit)? = null
) {
    val state by viewModel.settingsState.collectAsState()
    val homeState by viewModel.homeState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Rate state
    var hourlyRate by remember(state.hourlyRate) { mutableStateOf(state.hourlyRate.toInt().toString()) }
    var selectedCycle by remember(state.payrollCycleType) { mutableStateOf(state.payrollCycleType) }

    // Custom date range dialog states
    var customStartDate by remember(state.customStartDate) {
        mutableStateOf(if (state.customStartDate.isNotBlank()) state.customStartDate else homeState.periodStart)
    }
    var customEndDate by remember(state.customEndDate) {
        mutableStateOf(if (state.customEndDate.isNotBlank()) state.customEndDate else homeState.periodEnd)
    }
    var showRangePicker by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // ESS Account Manager states
    var showAccountsManagerDialog by remember { mutableStateOf(false) }
    var showAddAccountDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(homeState.message) {
        homeState.message?.let { msg ->
            android.widget.Toast.makeText(context, msg.text, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.restoreBackupFromUri(it) }
    }

    Scaffold(
        topBar = {
            KSTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ─── 1. Unified ESS Account Card (Home Style) ─────────────────────
            AccountCardHeaderBar(
                activeAccount = homeState.activeAccount,
                accountsCount = homeState.allAccounts.size,
                onOpenAccountManager = { showAccountsManagerDialog = true },
                onQuickAddAccount = { showAddAccountDialog = true }
            )

            // ─── 2. Hourly Rate Card ──────────────────────────────────────────
            SlateCard(
                title = stringResource(R.string.hourly_rate),
                icon = Icons.Filled.Payments
            ) {
                OutlinedTextField(
                    value = hourlyRate,
                    onValueChange = {
                        hourlyRate = it
                        it.toDoubleOrNull()?.let { rate -> viewModel.setHourlyRate(rate) }
                    },
                    label = { Text(stringResource(R.string.hourly_rate)) },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        Text(
                            stringResource(R.string.currency_egp) + "  ",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true
                )
            }

            // ─── 3. Payroll Cycle Card (Simplified: Auto 29-28 or Custom Range) ─
            SlateCard(
                title = stringResource(R.string.section_payroll_cycle),
                icon = Icons.Filled.CalendarMonth
            ) {
                // Active period indicator banner
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.active_period_banner, homeState.periodStart, homeState.periodEnd),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // The 2 clean options: Standard automated (16-15) or Custom Date Range
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Option 1: Automated 16 to 15
                    SlateOptionRow(
                        title = stringResource(R.string.cycle_16_15),
                        isSelected = selectedCycle != PayrollUtil.CYCLE_CUSTOM_RANGE,
                        onClick = {
                            selectedCycle = PayrollUtil.CYCLE_16_TO_15
                            viewModel.setPayrollCycleType(PayrollUtil.CYCLE_16_TO_15)
                        }
                    )

                    // Option 2: Custom Date Range
                    SlateOptionRow(
                        title = stringResource(R.string.cycle_custom_range),
                        isSelected = selectedCycle == PayrollUtil.CYCLE_CUSTOM_RANGE,
                        onClick = {
                            selectedCycle = PayrollUtil.CYCLE_CUSTOM_RANGE
                            val start = customStartDate.ifBlank { homeState.periodStart }
                            val end = customEndDate.ifBlank { homeState.periodEnd }
                            customStartDate = start
                            customEndDate = end
                            viewModel.setPayrollCycleType(PayrollUtil.CYCLE_CUSTOM_RANGE)
                            viewModel.setCustomPeriodRange(start, end, autoSync = false)
                            showRangePicker = true
                        }
                    )
                }

                // If Custom Date Range is selected, show modern period card
                AnimatedVisibility(visible = selectedCycle == PayrollUtil.CYCLE_CUSTOM_RANGE) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "الفترة المحددة حالياً:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = if (customStartDate.isNotBlank() && customEndDate.isNotBlank()) {
                                                "${customStartDate} ← ${customEndDate}"
                                            } else "لم يتم تحديد فترة بعد",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    val count = if (customStartDate.isNotBlank() && customEndDate.isNotBlank()) {
                                        DateUtil.calculateDaysCount(customStartDate, customEndDate)
                                    } else 0
                                    if (count > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "$count يوم",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { showRangePicker = true },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.DateRange,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("تحديد بالتقويم", fontWeight = FontWeight.Bold)
                                    }

                                    OutlinedButton(
                                        onClick = { viewModel.syncFromEss(silent = false) },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("مزامنة الفترة")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ─── 4. Fixed Employment & Overtime Card ───────────────────────────
            SlateCard(
                title = stringResource(R.string.section_employment_type),
                icon = Icons.Filled.Badge
            ) {
                SlateCleanToggleRow(
                    title = stringResource(R.string.fixed_employment_toggle),
                    subtitle = stringResource(R.string.fixed_employment_desc),
                    checked = state.isFixedEmployment,
                    onCheckedChange = { checked ->
                        viewModel.setFixedEmployment(checked)
                    }
                )
            }

            // ─── 5. Appearance & Time Format (Mihon-Style Theme Selector & 24h) ───
            SlateCard(
                title = stringResource(R.string.section_appearance),
                icon = Icons.Filled.Tune
            ) {
                // Theme Selection Row (Clickable Item with current mode subtitle)
                val currentThemeTitle = when (state.themeMode) {
                    PrefsUtil.THEME_LIGHT -> "الوضع الفاتح"
                    PrefsUtil.THEME_DARK  -> "الوضع الداكن (AMOLED)"
                    else                  -> "تلقائي (حسب النظام)"
                }
                val currentThemeIcon = when (state.themeMode) {
                    PrefsUtil.THEME_LIGHT -> Icons.Filled.LightMode
                    PrefsUtil.THEME_DARK  -> Icons.Filled.DarkMode
                    else                  -> Icons.Filled.BrightnessAuto
                }

                Surface(
                    onClick = { showThemeDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = currentThemeIcon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "المظهر",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currentThemeTitle,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.Filled.ChevronLeft,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // 24-Hour Switch (Only Title + Switch)
                SlateCleanToggleRow(
                    title = stringResource(R.string.time_format_24h),
                    checked = state.is24Hour,
                    onCheckedChange = { checked ->
                        viewModel.setTimeFormat(if (checked) "24h" else "12h")
                    }
                )
            }

            // ─── 5. Data & Backup Card (JSON Full Backup & Restore) ───────────
            SlateCard(
                title = "إدارة البيانات والنسخ الاحتياطي",
                icon = Icons.Filled.Storage
            ) {
                Text(
                    text = "نسخ احتياطي شامل لجميع السجلات والورديات والملاحظات وبيانات تسجيل الدخول والإعدادات في ملف JSON آمن ومستقل.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Create Backup Button
                Button(
                    onClick = { viewModel.createBackup() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("إنشاء نسخة احتياطية كاملة (JSON)", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Restore Backup Button
                OutlinedButton(
                    onClick = { restoreLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("استعادة نسخة احتياطية (JSON)", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // Custom Date Range Picker Dialog
        if (showRangePicker) {
            DateRangePickerDialog(
                initialStartDate = customStartDate.ifBlank { homeState.periodStart },
                initialEndDate = customEndDate.ifBlank { homeState.periodEnd },
                onConfirm = { start, end ->
                    showRangePicker = false
                    customStartDate = start
                    customEndDate = end
                    viewModel.setCustomPeriodRange(start, end, autoSync = true)
                },
                onDismiss = { showRangePicker = false }
            )
        }

        // Theme Selection Dialog (Mihon Style)
        if (showThemeDialog) {
            AlertDialog(
                onDismissRequest = { showThemeDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "اختر مظهر التطبيق",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeOptionItem(
                            title = "تلقائي (حسب النظام)",
                            icon = Icons.Filled.BrightnessAuto,
                            isSelected = state.themeMode == PrefsUtil.THEME_SYSTEM,
                            onClick = {
                                viewModel.setThemeMode(PrefsUtil.THEME_SYSTEM)
                                showThemeDialog = false
                            }
                        )
                        ThemeOptionItem(
                            title = "الوضع الداكن (AMOLED)",
                            icon = Icons.Filled.DarkMode,
                            isSelected = state.themeMode == PrefsUtil.THEME_DARK,
                            onClick = {
                                viewModel.setThemeMode(PrefsUtil.THEME_DARK)
                                showThemeDialog = false
                            }
                        )
                        ThemeOptionItem(
                            title = "الوضع الفاتح",
                            icon = Icons.Filled.LightMode,
                            isSelected = state.themeMode == PrefsUtil.THEME_LIGHT,
                            onClick = {
                                viewModel.setThemeMode(PrefsUtil.THEME_LIGHT)
                                showThemeDialog = false
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showThemeDialog = false }) {
                        Text("إلغاء", fontWeight = FontWeight.Bold)
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
                    showAddAccountDialog = true
                }
            )
        }

        // ─── Add Account Dialog ───────────────────────────────────────────────
        if (showAddAccountDialog) {
            AddAccountDialog(
                onDismiss = { showAddAccountDialog = false },
                onLogin = { email, password, onComplete ->
                    viewModel.addNewAccount(email, password, onComplete)
                }
            )
        }
    }
}

// ─── Sleek Slate Design Building Blocks ───────────────────────────────────────

@Composable
private fun SlateCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

@Composable
private fun SlateOptionRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun SlateCleanToggleRow(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ThemeOptionItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

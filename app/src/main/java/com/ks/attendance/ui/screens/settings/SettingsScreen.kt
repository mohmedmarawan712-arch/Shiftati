package com.ks.attendance.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.sp
import com.ks.attendance.R
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.components.DatePickerDialog
import com.ks.attendance.ui.components.KSTopBar
import com.ks.attendance.ui.theme.*
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
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // ESS states
    var essEmail by remember { mutableStateOf("") }
    var essPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var showAddAccountForm by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf<String?>(null) }

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

            // ─── 1. Unified ESS Accounts Card ─────────────────────────────────
            SlateCard(
                title = stringResource(R.string.section_ess_accounts),
                icon = Icons.Filled.ManageAccounts
            ) {
                val accounts = homeState.allAccounts
                val activeAccount = homeState.activeAccount

                if (accounts.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        accounts.forEach { acc ->
                            val isActive = (acc.id == activeAccount?.id)
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        if (!isActive) viewModel.switchAccount(acc)
                                    },
                                shape = RoundedCornerShape(14.dp),
                                color = if (isActive)
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                else
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                border = if (isActive)
                                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                                else null
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = acc.staffName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isActive) {
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Filled.CheckCircle,
                                                    contentDescription = stringResource(R.string.ess_active_badge),
                                                    tint = ColorWorked,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "كود الموظف: ${acc.staffId} • كود المطعم: ${acc.suId}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = acc.email,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.deleteAccount(acc) },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteOutline,
                                                contentDescription = stringResource(R.string.ess_delete_account),
                                                tint = ColorLogout,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Action buttons: Refresh active account & Toggle add form
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                isSyncing = true
                                viewModel.refreshActiveAccount {
                                    isSyncing = false
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.ess_refresh_data), style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        Button(
                            onClick = { showAddAccountForm = !showAddAccountForm },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = if (showAddAccountForm) Icons.Filled.Close else Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (showAddAccountForm) stringResource(R.string.ess_close_form) else stringResource(R.string.ess_add_account_btn),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Add Account Form (Shown when no accounts or user toggles add)
                AnimatedVisibility(
                    visible = accounts.isEmpty() || showAddAccountForm,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (accounts.isNotEmpty()) 14.dp else 0.dp)
                    ) {
                        if (accounts.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(bottom = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Text(
                                text = stringResource(R.string.ess_add_new_account_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        OutlinedTextField(
                            value = essEmail,
                            onValueChange = { essEmail = it },
                            label = { Text(stringResource(R.string.ess_email)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = essPassword,
                            onValueChange = { essPassword = it },
                            label = { Text(stringResource(R.string.ess_password)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            },
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = {
                                if (essEmail.isNotBlank() && essPassword.isNotBlank()) {
                                    isLoggingIn = true
                                    loginMessage = null
                                    viewModel.addNewAccount(essEmail.trim(), essPassword) { res ->
                                        isLoggingIn = false
                                        if (res.isSuccess) {
                                            essEmail = ""
                                            essPassword = ""
                                            showAddAccountForm = false
                                        } else {
                                            loginMessage = "فشل: ${res.exceptionOrNull()?.message}"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isLoggingIn && essEmail.isNotBlank() && essPassword.isNotBlank()
                        ) {
                            if (isLoggingIn) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.ess_logging_in_loading))
                            } else {
                                Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(stringResource(R.string.ess_login_and_save), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                loginMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

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
                            viewModel.setCustomPeriodRange(start, end)
                        }
                    )
                }

                // If Custom Date Range is selected, show exact Start and End Date pickers
                AnimatedVisibility(visible = selectedCycle == PayrollUtil.CYCLE_CUSTOM_RANGE) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Text(
                            text = "اختر تاريخ البداية والنهاية للفترة:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Start Date Picker Button
                            OutlinedButton(
                                onClick = { showStartDatePicker = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        stringResource(R.string.select_start_date),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        customStartDate.ifBlank { "اختر التاريخ" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // End Date Picker Button
                            OutlinedButton(
                                onClick = { showEndDatePicker = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        stringResource(R.string.select_end_date),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        customEndDate.ifBlank { "اختر التاريخ" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
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

        // Custom Start Date Picker Dialog
        if (showStartDatePicker) {
            val initialMillis = try {
                PayrollUtil.parseDate(customStartDate).time
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
            DatePickerDialog(
                initialMillis = initialMillis,
                onConfirm = { millis ->
                    showStartDatePicker = false
                    val formatted = PayrollUtil.formatDate(millis)
                    customStartDate = formatted
                    viewModel.setCustomPeriodRange(formatted, customEndDate)
                },
                onDismiss = { showStartDatePicker = false }
            )
        }

        // Custom End Date Picker Dialog
        if (showEndDatePicker) {
            val initialMillis = try {
                PayrollUtil.parseDate(customEndDate).time
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
            DatePickerDialog(
                initialMillis = initialMillis,
                onConfirm = { millis ->
                    showEndDatePicker = false
                    val formatted = PayrollUtil.formatDate(millis)
                    customEndDate = formatted
                    viewModel.setCustomPeriodRange(customStartDate, formatted)
                },
                onDismiss = { showEndDatePicker = false }
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

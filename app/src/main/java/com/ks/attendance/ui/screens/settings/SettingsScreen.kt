package com.ks.attendance.ui.screens.settings

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
import com.ks.attendance.ui.components.KSTopBar
import com.ks.attendance.ui.theme.ColorWorked
import com.ks.attendance.utils.PayrollUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.settingsState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Form states
    var employeeName by remember(state.employeeName) { mutableStateOf(state.employeeName) }
    var hourlyRate by remember(state.hourlyRate) { mutableStateOf(state.hourlyRate.toInt().toString()) }
    var payrollStartDay by remember(state.payrollStartDay) { mutableIntStateOf(state.payrollStartDay) }
    var selectedCycle by remember(state.payrollCycleType) { mutableStateOf(state.payrollCycleType) }

    // ESS states
    var essEmail by remember(state.essEmail) { mutableStateOf(state.essEmail) }
    var essPassword by remember(state.essPassword) { mutableStateOf(state.essPassword) }
    var saveCreds by remember(state.essSaveCredentials) { mutableStateOf(state.essSaveCredentials) }
    var showPassword by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            KSTopBar(title = stringResource(R.string.settings_title), onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            // ─── 1. Profile & Rate Card ──────────────────────────────────────
            SlateCard(
                title = stringResource(R.string.section_profile),
                icon = Icons.Filled.PersonOutline
            ) {
                OutlinedTextField(
                    value = employeeName,
                    onValueChange = {
                        employeeName = it
                        viewModel.setEmployeeName(it)
                    },
                    label = { Text(stringResource(R.string.employee_name)) },
                    placeholder = { Text(stringResource(R.string.employee_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

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

            // ─── 2. Payroll Cycle Card ───────────────────────────────────────
            SlateCard(
                title = stringResource(R.string.section_payroll_cycle),
                icon = Icons.Filled.CalendarMonth
            ) {
                val cycleOptions = listOf(
                    PayrollUtil.CYCLE_29_TO_28 to stringResource(R.string.cycle_29_28),
                    PayrollUtil.CYCLE_CURRENT_MONTH to stringResource(R.string.cycle_current_month),
                    PayrollUtil.CYCLE_PREVIOUS_MONTH to stringResource(R.string.cycle_previous_month),
                    PayrollUtil.CYCLE_BOTH_MONTHS to stringResource(R.string.cycle_both_months),
                    PayrollUtil.CYCLE_CUSTOM_DAY to stringResource(R.string.cycle_custom)
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    cycleOptions.forEach { (cycleKey, label) ->
                        val isSelected = selectedCycle == cycleKey
                        SlateOptionRow(
                            title = label,
                            isSelected = isSelected,
                            onClick = {
                                selectedCycle = cycleKey
                                viewModel.setPayrollCycleType(cycleKey)
                            }
                        )
                    }
                }

                AnimatedVisibility(visible = selectedCycle == PayrollUtil.CYCLE_CUSTOM_DAY) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.custom_start_day, payrollStartDay),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Slider(
                            value = payrollStartDay.toFloat(),
                            onValueChange = {
                                payrollStartDay = it.toInt()
                                viewModel.setPayrollStartDay(it.toInt())
                            },
                            valueRange = 1f..31f,
                            steps = 29
                        )
                    }
                }
            }

            // ─── 3. Appearance & Time Format Card ────────────────────────────
            SlateCard(
                title = stringResource(R.string.section_appearance),
                icon = Icons.Filled.Tune
            ) {
                // 24-Hour Switch
                SlateToggleRow(
                    title = stringResource(R.string.time_format_24h),
                    subtitle = if (state.is24Hour) "18:30 (عسكري)" else "06:30 م (ص/م)",
                    checked = state.is24Hour,
                    onCheckedChange = { checked ->
                        viewModel.setTimeFormat(if (checked) "24h" else "12h")
                    }
                )

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
                )

                // AMOLED Switch
                SlateToggleRow(
                    title = stringResource(R.string.amoled_dark_mode),
                    subtitle = stringResource(R.string.amoled_desc),
                    checked = state.darkMode,
                    onCheckedChange = { viewModel.setDarkMode(it) }
                )
            }

            // ─── 4. ESS Integration Card ──────────────────────────────────────
            SlateCard(
                title = stringResource(R.string.section_ess),
                icon = Icons.Filled.CloudQueue
            ) {
                if (state.isEssLoggedIn) {
                    // Connected Status
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ColorWorked.copy(alpha = 0.12f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(ColorWorked)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (state.employeeName.isNotBlank()) state.employeeName else stringResource(R.string.ess_connected),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (state.essEmail.isNotBlank()) {
                                Text(
                                    text = state.essEmail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                isSyncing = true
                                viewModel.syncFromEss { count ->
                                    isSyncing = false
                                    loginMessage = "تمت المزامنة بنجاح: $count وردية"
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !isSyncing
                        ) {
                            if (isSyncing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.ess_sync_btn))
                            }
                        }

                        OutlinedButton(
                            onClick = { viewModel.essLogout() },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.ess_logout_btn))
                        }
                    }
                } else {
                    // Login Form
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

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { saveCreds = !saveCreds },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = saveCreds,
                            onCheckedChange = { saveCreds = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.ess_remember_me),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (essEmail.isNotBlank() && essPassword.isNotBlank()) {
                                isLoggingIn = true
                                viewModel.essLogin(essEmail.trim(), essPassword, saveCreds) { res ->
                                    isLoggingIn = false
                                    if (res.isSuccess) {
                                        val instances = res.getOrDefault(emptyList())
                                        if (instances.isNotEmpty()) {
                                            viewModel.essSelectInstance(instances.first())
                                        }
                                        loginMessage = "تم تسجيل الدخول بنجاح"
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
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.ess_login_btn))
                        }
                    }
                }

                loginMessage?.let { msg ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
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
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                shape = RoundedCornerShape(18.dp)
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
                    color = MaterialTheme.colorScheme.onBackground
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
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.dp else 0.5.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
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
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun SlateToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

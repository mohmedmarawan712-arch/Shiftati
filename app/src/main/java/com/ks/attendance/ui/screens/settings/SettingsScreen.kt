package com.ks.attendance.ui.screens.settings

import androidx.compose.animation.*
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ks.attendance.data.remote.EssInstance
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.components.ConfirmDeleteDialog
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

    // ESS form states
    var essEmail by remember(state.essEmail) { mutableStateOf(state.essEmail) }
    var essPassword by remember(state.essPassword) { mutableStateOf(state.essPassword) }
    var saveCreds by remember(state.essSaveCredentials) { mutableStateOf(state.essSaveCredentials) }
    var showPassword by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var loginMessage by remember { mutableStateOf<String?>(null) }
    var showInstanceSelector by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            KSTopBar(title = "الإعدادات", onBack = onBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            // ─── Profile Section ──────────────────────────────────────────────
            SettingsSectionCard(title = "الملف الشخصي", icon = Icons.Filled.Person) {
                OutlinedTextField(
                    value = employeeName,
                    onValueChange = {
                        employeeName = it
                        viewModel.setEmployeeName(it)
                    },
                    label = { Text("اسم الموظف") },
                    placeholder = { Text("اكتب اسمك أو سيتم جلبه من ESS") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    singleLine = true
                )
            }

            // ─── Salary & Payroll Cycle Section ───────────────────────────────
            SettingsSectionCard(title = "إعدادات الراتب والدورة", icon = Icons.Filled.AccountBalanceWallet) {
                // Hourly Rate
                OutlinedTextField(
                    value = hourlyRate,
                    onValueChange = {
                        hourlyRate = it
                        it.toDoubleOrNull()?.let { rate -> viewModel.setHourlyRate(rate) }
                    },
                    label = { Text("سعر الساعة (جنيه)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = { Text("ج.م  ", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "طريقة احتساب دورة الشهر:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Cycle selector chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CycleOptionChip(
                        title = "تلقائي: من يوم 29 إلى يوم 28 (الافتراضي)",
                        subtitle = "حساب دورة الراتب تلقائياً مع مراعاة الأشهر 31 يوم وفبراير 28/29 يوم",
                        isSelected = selectedCycle == PayrollUtil.CYCLE_29_TO_28,
                        onClick = {
                            selectedCycle = PayrollUtil.CYCLE_29_TO_28
                            viewModel.setPayrollCycleType(PayrollUtil.CYCLE_29_TO_28)
                        }
                    )

                    CycleOptionChip(
                        title = "الشهر الحالي (من 1 حتى آخر يوم)",
                        subtitle = "يحسب الأيام الفعلية تلقائياً (30 أو 31 أو 28/29 في فبراير)",
                        isSelected = selectedCycle == PayrollUtil.CYCLE_CURRENT_MONTH,
                        onClick = {
                            selectedCycle = PayrollUtil.CYCLE_CURRENT_MONTH
                            viewModel.setPayrollCycleType(PayrollUtil.CYCLE_CURRENT_MONTH)
                        }
                    )

                    CycleOptionChip(
                        title = "الشهر السابق كاملاً",
                        subtitle = "لعرض وحساب ساعات ورواتب الشهر المنقضي",
                        isSelected = selectedCycle == PayrollUtil.CYCLE_PREVIOUS_MONTH,
                        onClick = {
                            selectedCycle = PayrollUtil.CYCLE_PREVIOUS_MONTH
                            viewModel.setPayrollCycleType(PayrollUtil.CYCLE_PREVIOUS_MONTH)
                        }
                    )

                    CycleOptionChip(
                        title = "الشهرين معاً (السابق + الحالي)",
                        subtitle = "لحساب كامل الفترتين معاً",
                        isSelected = selectedCycle == PayrollUtil.CYCLE_BOTH_MONTHS,
                        onClick = {
                            selectedCycle = PayrollUtil.CYCLE_BOTH_MONTHS
                            viewModel.setPayrollCycleType(PayrollUtil.CYCLE_BOTH_MONTHS)
                        }
                    )

                    CycleOptionChip(
                        title = "دورة مخصصة تبدأ بيوم محدد",
                        subtitle = "مثلاً: من يوم 26 حتى 25 من الشهر التالي",
                        isSelected = selectedCycle == PayrollUtil.CYCLE_CUSTOM_DAY,
                        onClick = {
                            selectedCycle = PayrollUtil.CYCLE_CUSTOM_DAY
                            viewModel.setPayrollCycleType(PayrollUtil.CYCLE_CUSTOM_DAY)
                        }
                    )
                }

                // If custom day is selected, show day number picker
                AnimatedVisibility(visible = selectedCycle == PayrollUtil.CYCLE_CUSTOM_DAY) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("يوم بداية الدورة:", style = MaterialTheme.typography.bodySmall)
                            Text(
                                "يوم $payrollStartDay في الشهر",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
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

            // ─── Display & Theme ──────────────────────────────────────────────
            SettingsSectionCard(title = "المظهر ونظام الوقت", icon = Icons.Filled.DarkMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "وضع AMOLED الداكن",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "خلفية سوداء بالكامل 100% لتوفير طاقة الشاشة ومظهر فخم",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = state.darkMode,
                        onCheckedChange = { viewModel.setDarkMode(it) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "تنسيق الوقت",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (state.is24Hour) "نظام 24 ساعة (مثال: 17:30)" else "نظام 12 ساعة (مثال: 05:30 م)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = !state.is24Hour,
                            onClick = { viewModel.setTimeFormat("12h") },
                            label = { Text("12 ساعة (ص/م)") }
                        )
                        FilterChip(
                            selected = state.is24Hour,
                            onClick = { viewModel.setTimeFormat("24h") },
                            label = { Text("24 ساعة") }
                        )
                    }
                }
            }

            // ─── ESS Integration ──────────────────────────────────────────────
            SettingsSectionCard(title = "الربط مع نظام ESS (أونلاين)", icon = Icons.Filled.CloudSync) {
                if (state.isEssLoggedIn) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = ColorWorked.copy(alpha = 0.15f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = ColorWorked)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (state.employeeName.isNotBlank()) "الموظف: ${state.employeeName}" else "متصل بنظام ESS",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(state.essEmail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = {
                                    viewModel.syncFromEss { count ->
                                        loginMessage = "تمت المزامنة بنجاح: $count وردية"
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("مزامنة الآن")
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = essEmail,
                        onValueChange = { essEmail = it },
                        label = { Text("البريد الإلكتروني") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = essPassword,
                        onValueChange = { essPassword = it },
                        label = { Text("كلمة المرور") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                    contentDescription = null
                                )
                            }
                        },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Remember credentials checkbox
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = saveCreds,
                            onCheckedChange = { saveCreds = it }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "حفظ البريد وكلمة المرور للدخول التلقائي",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

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
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        enabled = !isLoggingIn && essEmail.isNotBlank() && essPassword.isNotBlank()
                    ) {
                        if (isLoggingIn) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Login, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("تسجيل الدخول ومزامنة الورديات")
                        }
                    }
                }

                loginMessage?.let {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(22.dp)
            )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
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
private fun CycleOptionChip(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = isSelected,
                onClick = onClick,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

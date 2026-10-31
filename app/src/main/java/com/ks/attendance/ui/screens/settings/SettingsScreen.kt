package com.ks.attendance.ui.screens.settings

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
import com.ks.attendance.data.remote.EssInstance
import com.ks.attendance.ui.MainViewModel
import com.ks.attendance.ui.components.ConfirmDeleteDialog
import com.ks.attendance.ui.components.KSTopBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.settingsState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Local form state
    var employeeName by remember(state.employeeName) { mutableStateOf(state.employeeName) }
    var hourlyRate by remember(state.hourlyRate) { mutableStateOf(state.hourlyRate.toString()) }
    var payrollStartDay by remember(state.payrollStartDay) { mutableStateOf(state.payrollStartDay) }
    var essEmail by remember { mutableStateOf("") }
    var essPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isLoggingIn by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var showConfirmClear by remember { mutableStateOf(false) }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ─── الملف الشخصي ───────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Person, title = "الملف الشخصي")
            OutlinedTextField(
                value = employeeName,
                onValueChange = { employeeName = it },
                label = { Text("الاسم") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (employeeName != state.employeeName) {
                        IconButton(onClick = { viewModel.setEmployeeName(employeeName) }) {
                            Icon(Icons.Default.Check, contentDescription = "حفظ")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(8.dp))

            // ─── إعدادات الراتب ─────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Payments, title = "إعدادات الراتب")
            OutlinedTextField(
                value = hourlyRate,
                onValueChange = { hourlyRate = it },
                label = { Text("سعر الساعة (ج.م)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                trailingIcon = {
                    val rateDouble = hourlyRate.toDoubleOrNull()
                    if (rateDouble != null && rateDouble != state.hourlyRate) {
                        IconButton(onClick = { viewModel.setHourlyRate(rateDouble) }) {
                            Icon(Icons.Default.Check, contentDescription = "حفظ")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(Modifier.height(4.dp))
            Text(
                text = "يوم بداية دورة الرواتب: $payrollStartDay",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Slider(
                value = payrollStartDay.toFloat(),
                onValueChange = { payrollStartDay = it.toInt() },
                onValueChangeFinished = { viewModel.setPayrollStartDay(payrollStartDay) },
                valueRange = 1f..28f,
                steps = 26
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("1", style = MaterialTheme.typography.labelSmall)
                Text("28", style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(8.dp))

            // ─── المظهر ──────────────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Palette, title = "المظهر")
            PreferenceItem(
                title = "اللغة",
                subtitle = if (state.language == "ar") "العربية" else "English",
                icon = Icons.Default.Language,
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(
                            selected = state.language == "ar",
                            onClick = { viewModel.setLanguage("ar") },
                            label = { Text("عربي") }
                        )
                        FilterChip(
                            selected = state.language == "en",
                            onClick = { viewModel.setLanguage("en") },
                            label = { Text("EN") }
                        )
                    }
                }
            )
            PreferenceItem(
                title = "الوضع الداكن",
                subtitle = if (state.darkMode) "مفعّل" else "مُعطَّل",
                icon = Icons.Default.DarkMode,
                trailing = {
                    Switch(
                        checked = state.darkMode,
                        onCheckedChange = { viewModel.setDarkMode(it) }
                    )
                }
            )

            Spacer(Modifier.height(8.dp))

            // ─── الاتصال بـ ESS ──────────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Cloud, title = "الاتصال بـ ESS (اختياري)")

            if (state.isEssLoggedIn) {
                // Logged in status
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "متصل بـ ESS",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (state.essEmail.isNotBlank()) {
                                Text(
                                    text = state.essEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }
            } else {
                // Login form
                OutlinedTextField(
                    value = essEmail,
                    onValueChange = { essEmail = it },
                    label = { Text("البريد الإلكتروني") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    shape = RoundedCornerShape(12.dp)
                )
                OutlinedTextField(
                    value = essPassword,
                    onValueChange = { essPassword = it },
                    label = { Text("كلمة المرور") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None
                                           else PasswordVisualTransformation(),
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )

                loginError?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Button(
                    onClick = {
                        loginError = null
                        isLoggingIn = true
                        viewModel.essLogin(essEmail, essPassword) { result ->
                            isLoggingIn = false
                            if (result.isSuccess) {
                                val instances = result.getOrDefault(emptyList())
                                if (instances.size == 1) {
                                    viewModel.essSelectInstance(instances.first())
                                } else if (instances.size > 1) {
                                    showInstanceSelector = true
                                }
                            } else {
                                loginError = "فشل تسجيل الدخول: ${result.exceptionOrNull()?.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoggingIn && essEmail.isNotBlank() && essPassword.isNotBlank()
                ) {
                    if (isLoggingIn) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Login, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("تسجيل الدخول إلى ESS")
                }
            }

            // Instance selector
            if (showInstanceSelector && state.essInstances.isNotEmpty()) {
                EssInstanceSelector(
                    instances = state.essInstances,
                    onSelect = { instance ->
                        viewModel.essSelectInstance(instance)
                        showInstanceSelector = false
                    },
                    onDismiss = { showInstanceSelector = false }
                )
            }

            Spacer(Modifier.height(8.dp))

            // ─── تصدير وعن التطبيق ──────────────────────────────────────────
            SectionHeader(icon = Icons.Default.Info, title = "تصدير وعن التطبيق")
            PreferenceItem(
                title = "إصدار التطبيق",
                subtitle = "1.0.0",
                icon = Icons.Default.AppSettingsAlt
            )

            OutlinedButton(
                onClick = { showConfirmClear = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(Icons.Default.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("مسح جميع البيانات")
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showConfirmClear) {
        ConfirmDeleteDialog(
            message = "سيتم حذف جميع سجلات الحضور والملاحظات نهائياً. هل أنت متأكد؟",
            onConfirm = {
                // Clear all data — viewModel can expose this; for now dismiss
                showConfirmClear = false
            },
            onDismiss = { showConfirmClear = false }
        )
    }
}

// ─── Section Header ─────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(4.dp))
}

// ─── Preference Item ─────────────────────────────────────────────────────────

@Composable
fun PreferenceItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick ?: {},
        enabled = onClick != null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            if (trailing != null) {
                trailing()
            }
        }
    }
}

// ─── ESS Instance Selector Dialog ────────────────────────────────────────────

@Composable
private fun EssInstanceSelector(
    instances: List<EssInstance>,
    onSelect: (EssInstance) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر الحساب") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                instances.forEach { instance ->
                    OutlinedButton(
                        onClick = { onSelect(instance) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(instance.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("إلغاء") }
        }
    )
}

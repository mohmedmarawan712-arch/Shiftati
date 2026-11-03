package com.ks.attendance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.data.local.entity.DayNote
import com.ks.attendance.data.local.entity.EssAccount
import com.ks.attendance.data.remote.EssInstance
import com.ks.attendance.data.repository.AttendanceRepository
import com.ks.attendance.utils.ExportUtil
import com.ks.attendance.utils.PayrollUtil
import com.ks.attendance.utils.PrefsUtil
import com.ks.attendance.utils.qr.QrUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.ks.attendance.utils.BackupUtil
import java.text.SimpleDateFormat
import java.util.*

data class UiMessage(val text: String, val isError: Boolean = false)

data class HomeState(
    val openSession: AttendanceRecord? = null,
    val todayRecords: List<AttendanceRecord> = emptyList(),
    val todayHours: Double = 0.0,
    val monthHours: Double = 0.0,
    val monthSalary: Double = 0.0,
    val periodStart: String = "",
    val periodEnd: String = "",
    val employeeName: String = "",
    val workedDays: Int = 0,
    val is24Hour: Boolean = false,
    val isLoading: Boolean = false,
    val message: UiMessage? = null,
    val isFixedEmployment: Boolean = false,
    val standardShiftHours: Double = 9.0,
    val regularHours: Double = 0.0,
    val overtimeHours: Double = 0.0,
    val regularSalary: Double = 0.0,
    val overtimeSalary: Double = 0.0,
    val hourlyRate: Double = 30.0,
    val hasChosenEmployment: Boolean = true,
    val activeAccount: EssAccount? = null,
    val allAccounts: List<EssAccount> = emptyList()
)

data class ReportState(
    val records: List<AttendanceRecord> = emptyList(),
    val notes: List<DayNote> = emptyList(),
    val totalHours: Double = 0.0,
    val estimatedSalary: Double = 0.0,
    val hourlyRate: Double = 30.0,
    val periodStart: String = "",
    val periodEnd: String = "",
    val workedDays: Int = 0,
    val isExporting: Boolean = false,
    val exportedPath: String? = null,
    val isFixedEmployment: Boolean = false,
    val regularHours: Double = 0.0,
    val overtimeHours: Double = 0.0
)

data class SettingsState(
    val hourlyRate: Double = 30.0,
    val payrollStartDay: Int = 16,
    val payrollCycleType: String = PayrollUtil.CYCLE_16_TO_15,
    val language: String = "ar",
    val darkMode: Boolean = true,
    val themeMode: String = "system", // "system" | "dark" | "light"
    val timeFormat: String = "12h",
    val essEmail: String = "",
    val essPassword: String = "",
    val essSaveCredentials: Boolean = false,
    val employeeName: String = "",
    val customStartDate: String = "",
    val customEndDate: String = "",
    val isEssLoggedIn: Boolean = false,
    val essInstances: List<EssInstance> = emptyList(),
    val isFixedEmployment: Boolean = false,
    val standardShiftHours: Double = 9.0,
    val hasChosenEmployment: Boolean = true
) {
    val is24Hour: Boolean get() = timeFormat == "24h"
}

class MainViewModel(
    private val repo: AttendanceRepository,
    private val prefs: PrefsUtil,
    private val context: Context
) : ViewModel() {

    private val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val today get() = sdfDate.format(Date())

    // ─── Home ─────────────────────────────────────────────────────────────────

    private val _homeState = MutableStateFlow(HomeState())
    val homeState: StateFlow<HomeState> = _homeState.asStateFlow()

    private val _settingsState = MutableStateFlow(SettingsState())
    val settingsState: StateFlow<SettingsState> = _settingsState.asStateFlow()

    // ─── Records & Notes ──────────────────────────────────────────────────────

    val allRecords: StateFlow<List<AttendanceRecord>> = repo.getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedDate = MutableStateFlow(today)
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    val dayRecords: StateFlow<List<AttendanceRecord>> = _selectedDate.flatMapLatest {
        repo.getRecordsForDay(it)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dayNotes: StateFlow<List<DayNote>> = _selectedDate.flatMapLatest {
        repo.getNotesForDay(it)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Accounts & Multi-Profile ───────────────────────────────────────────

    val allAccounts: StateFlow<List<EssAccount>> = repo.getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAccount: StateFlow<EssAccount?> = repo.getActiveAccount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // ─── Report ───────────────────────────────────────────────────────────────

    private val _reportState = MutableStateFlow(ReportState())
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    init {
        loadSettings()
        viewModelScope.launch {
            repo.cleanPlaceholderTodayOffDay()
            repo.deduplicateRecords()
            refreshHome()
        }
        viewModelScope.launch {
            repo.getRecordsForDay(today).collect { records ->
                _homeState.value = _homeState.value.copy(todayRecords = records)
            }
        }
        viewModelScope.launch {
            allAccounts.collect { list ->
                _homeState.value = _homeState.value.copy(allAccounts = list)
            }
        }
        viewModelScope.launch {
            activeAccount.collect { acc ->
                if (acc != null) {
                    val name = acc.staffShortName.ifBlank { acc.staffName }
                    _homeState.value = _homeState.value.copy(
                        activeAccount = acc,
                        employeeName = name
                    )
                } else {
                    _homeState.value = _homeState.value.copy(
                        activeAccount = null,
                        employeeName = "",
                        todayRecords = emptyList(),
                        openSession = null
                    )
                }
                refreshHome()
            }
        }
        // Keep home metrics in sync whenever records change
        viewModelScope.launch {
            allRecords.collect {
                refreshHome()
            }
        }
        // Keep open session reactively updated
        viewModelScope.launch {
            repo.observeOpenSession().collect { open ->
                _homeState.value = _homeState.value.copy(openSession = open)
            }
        }
        // Auto-sync from ESS on launch if credentials exist
        autoLoginAndSyncIfSaved()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine(prefs.allPreferences, activeAccount) { p, acc ->
                val accId = acc?.id ?: 0L
                val rate = prefs.getHourlyRate(p, accId)
                val startDay = prefs.getPayrollStartDay(p, accId)
                val cycleType = prefs.getPayrollCycleType(p, accId)
                val lang = p[PrefsUtil.KEY_LANGUAGE] ?: PrefsUtil.DEFAULT_LANGUAGE
                val dark = p[PrefsUtil.KEY_DARK_MODE] ?: true
                val theme = p[PrefsUtil.KEY_THEME_MODE] ?: PrefsUtil.DEFAULT_THEME_MODE
                val timeFmt = p[PrefsUtil.KEY_TIME_FORMAT] ?: PrefsUtil.DEFAULT_TIME_FORMAT
                val email = acc?.email ?: (p[PrefsUtil.KEY_ESS_EMAIL] ?: "")
                val pass = acc?.password ?: (p[PrefsUtil.KEY_ESS_PASSWORD] ?: "")
                val saveCreds = p[PrefsUtil.KEY_ESS_SAVE_CREDS] ?: false
                val name = acc?.let { it.staffShortName.ifBlank { it.staffName } } ?: (p[PrefsUtil.KEY_EMPLOYEE_NAME] ?: "")
                val customStart = prefs.getCustomStartDate(p, accId)
                val customEnd = prefs.getCustomEndDate(p, accId)
                val isFixed = prefs.getFixedEmployment(p, accId)
                val stdShift = prefs.getStandardShiftHours(p, accId)
                val hasChosen = prefs.getHasChosenEmployment(p, accId)

                SettingsState(
                    hourlyRate = rate,
                    payrollStartDay = startDay,
                    payrollCycleType = cycleType,
                    language = lang,
                    darkMode = dark,
                    themeMode = theme,
                    timeFormat = timeFmt,
                    essEmail = email,
                    essPassword = pass,
                    essSaveCredentials = saveCreds,
                    isEssLoggedIn = acc != null || repo.isEssLoggedIn,
                    employeeName = name,
                    customStartDate = customStart,
                    customEndDate = customEnd,
                    isFixedEmployment = isFixed,
                    standardShiftHours = stdShift,
                    hasChosenEmployment = hasChosen
                ) to (isFixed to (rate to (stdShift to hasChosen)))
            }.collect { (settings, homeAttrs) ->
                _settingsState.value = settings
                val (isFixed, rateAndStd) = homeAttrs
                val (rate, stdAndChosen) = rateAndStd
                val (stdShift, hasChosen) = stdAndChosen
                _homeState.value = _homeState.value.copy(
                    employeeName = settings.employeeName,
                    is24Hour = settings.is24Hour,
                    isFixedEmployment = isFixed,
                    standardShiftHours = stdShift,
                    hourlyRate = rate,
                    hasChosenEmployment = hasChosen
                )
                refreshHome()
            }
        }
    }

    private fun loadSettings() {
        viewModelScope.launch {
            _settingsState.value = _settingsState.value.copy(
                isEssLoggedIn = repo.isEssLoggedIn
            )
        }
    }

    fun refreshHome() {
        viewModelScope.launch {
            val accId = _homeState.value.activeAccount?.id ?: repo.getActiveAccountId()
            val p = prefs.allPreferences.first()
            val rate = prefs.getHourlyRate(p, accId)
            val startDay = prefs.getPayrollStartDay(p, accId)
            val cycleType = prefs.getPayrollCycleType(p, accId)
            val customStart = prefs.getCustomStartDate(p, accId)
            val customEnd = prefs.getCustomEndDate(p, accId)
            val isFixed = prefs.getFixedEmployment(p, accId)
            val stdShift = prefs.getStandardShiftHours(p, accId)
            val hasChosen = prefs.getHasChosenEmployment(p, accId)

            val (periodStart, periodEnd) = PayrollUtil.resolvePeriod(
                cycleType = cycleType,
                startDay = startDay,
                customStart = customStart,
                customEnd = customEnd,
                referenceDate = Date()
            )

            val openSession = repo.getOpenSession()
            val todayRaw = repo.getTotalHoursInRange(today, today)
            val todayHours = if (isFixed) todayRaw else (if (todayRaw > 0.5) todayRaw - 0.5 else 0.0)
            val recordsInRange = repo.getRecordsInRangeDirect(periodStart, periodEnd)

            val workedRecords = recordsInRange.filter { it.hoursWorked > 0.0 && it.source != "off_day" }
            val dayGroups = workedRecords.groupBy { it.workDate }
            val workedDays = dayGroups.keys.size

            var overtimeHours = 0.0
            dayGroups.forEach { (_, dayRecs) ->
                val daySum = dayRecs.sumOf { it.hoursWorked }
                if (daySum > stdShift) {
                    overtimeHours += (daySum - stdShift)
                }
            }

            // Deduct 0.5h break per worked day for Hourly mode
            val netMonthHours = dayGroups.values.sumOf { dayRecs ->
                val daySum = dayRecs.sumOf { it.hoursWorked }
                if (daySum > 0.5) daySum - 0.5 else 0.0
            }

            val monthHours = if (isFixed) recordsInRange.sumOf { it.hoursWorked } else netMonthHours
            val regularHours = if (isFixed) (workedDays * stdShift) else netMonthHours
            val regularSalary = if (isFixed) 0.0 else (netMonthHours * rate)
            val overtimeSalary = overtimeHours * rate
            val monthSalary = if (isFixed) {
                overtimeSalary
            } else {
                netMonthHours * rate
            }


            val activeAcc = repo.getActiveAccountDirect()
            val allAccs = repo.getAllAccountsDirect()
            val resolvedName = activeAcc?.staffShortName?.ifBlank { activeAcc.staffName } ?: ""

            _homeState.value = _homeState.value.copy(
                openSession = openSession,
                periodStart = periodStart,
                periodEnd = periodEnd,
                todayHours = todayHours,
                monthHours = monthHours,
                monthSalary = monthSalary,
                workedDays = workedDays,
                isFixedEmployment = isFixed,
                standardShiftHours = stdShift,
                regularHours = regularHours,
                overtimeHours = overtimeHours,
                regularSalary = regularSalary,
                overtimeSalary = overtimeSalary,
                hourlyRate = rate,
                hasChosenEmployment = hasChosen,
                activeAccount = activeAcc,
                allAccounts = allAccs,
                employeeName = resolvedName
            )
        }
    }

    fun selectInitialEmploymentType(isFixed: Boolean) {
        viewModelScope.launch {
            val accId = _homeState.value.activeAccount?.id ?: repo.getActiveAccountId()
            prefs.setFixedEmployment(isFixed, accId)
            prefs.setHasChosenEmployment(true, accId)
            refreshHome()
            loadReport()
        }
    }

    // ─── Clock In/Out ─────────────────────────────────────────────────────────

    fun clockIn(customTime: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            try {
                val existing = repo.getOpenSession()
                if (existing != null) {
                    showHomeMessage("أنت مسجل حضور بالفعل منذ الساعة ${PayrollUtil.formatTime(existing.checkInTime)}", isError = true)
                    return@launch
                }
                repo.clockIn(customTime)
                refreshHome()
                showHomeMessage("تم تسجيل الحضور الساعة ${PayrollUtil.formatTime(customTime)} ✓")
            } catch (e: Exception) {
                showHomeMessage("خطأ: ${e.message}", true)
            }
        }
    }

    fun clockOut(customTime: Long = System.currentTimeMillis()) {
        viewModelScope.launch {
            try {
                val open = repo.getOpenSession()
                    ?: run { showHomeMessage("لا توجد جلسة حضور مفتوحة لتسجيل انصرافها", true); return@launch }
                val updated = repo.clockOut(open, customTime)
                refreshHome()
                showHomeMessage("تم تسجيل الانصراف الساعة ${PayrollUtil.formatTime(updated.checkOutTime ?: customTime)} ✓ (${PayrollUtil.formatHours(updated.hoursWorked)})")
            } catch (e: Exception) {
                showHomeMessage("خطأ: ${e.message}", true)
            }
        }
    }

    // ─── CRUD ─────────────────────────────────────────────────────────────────

    fun addRecord(record: AttendanceRecord) {
        viewModelScope.launch {
            repo.insertRecord(record)
            refreshHome()
        }
    }

    fun updateRecord(record: AttendanceRecord) {
        viewModelScope.launch {
            val existing = repo.getRecordById(record.id)
            val checkInDiffMinutes = if (existing != null) kotlin.math.abs(existing.checkInTime - record.checkInTime) / 60000 else 999
            val checkOutDiffMinutes = if (existing != null && existing.checkOutTime != null && record.checkOutTime != null) {
                kotlin.math.abs(existing.checkOutTime - record.checkOutTime) / 60000
            } else if (existing?.checkOutTime == null && record.checkOutTime == null) {
                0L
            } else {
                999L
            }
            val timeOrDateChanged = existing != null && (
                existing.workDate != record.workDate ||
                checkInDiffMinutes >= 1L ||
                checkOutDiffMinutes >= 1L
            )

            // If time/date was NOT changed, preserve the original isFromServer flag!
            val finalIsFromServer = if (timeOrDateChanged) {
                false
            } else if (existing != null) {
                existing.isFromServer
            } else {
                record.isFromServer
            }

            val userEditedRecord = record.copy(isFromServer = finalIsFromServer)
            repo.updateRecord(userEditedRecord)
            refreshHome()
        }
    }

    fun deleteRecord(record: AttendanceRecord) {
        viewModelScope.launch {
            repo.deleteRecord(record)
            refreshHome()
        }
    }

    fun deleteDay(date: String) {
        viewModelScope.launch {
            repo.deleteDay(date)
            refreshHome()
            showHomeMessage("تم حذف جميع سجلات يوم $date بنجاح")
        }
    }

    fun selectDate(date: String) { _selectedDate.value = date }

    // ─── Notes ───────────────────────────────────────────────────────────────

    fun addNote(date: String, text: String) {
        viewModelScope.launch {
            repo.insertNote(DayNote(date = date, noteText = text))
        }
    }

    fun updateNote(note: DayNote) {
        viewModelScope.launch { repo.updateNote(note) }
    }

    fun deleteNote(note: DayNote) {
        viewModelScope.launch { repo.deleteNote(note) }
    }

    // ─── Reports ─────────────────────────────────────────────────────────────

    fun loadReport(start: String? = null, end: String? = null) {
        viewModelScope.launch {
            val accId = _homeState.value.activeAccount?.id ?: repo.getActiveAccountId()
            val p = prefs.allPreferences.first()
            val rate = prefs.getHourlyRate(p, accId)
            val startDay = prefs.getPayrollStartDay(p, accId)
            val cycleType = prefs.getPayrollCycleType(p, accId)
            val customStart = prefs.getCustomStartDate(p, accId)
            val customEnd = prefs.getCustomEndDate(p, accId)
            val isFixed = prefs.getFixedEmployment(p, accId)
            val stdShift = prefs.getStandardShiftHours(p, accId)

            val (ps, pe) = if (start != null && end != null) start to end
                           else PayrollUtil.resolvePeriod(
                               cycleType = cycleType,
                               startDay = startDay,
                               customStart = customStart,
                               customEnd = customEnd,
                               referenceDate = Date()
                           )

            repo.getRecordsInRange(ps, pe).collect { records ->
                val workedRecords = records.filter { it.hoursWorked > 0.0 && it.source != "off_day" }
                val dayGroups = workedRecords.groupBy { it.workDate }
                val workedDays = dayGroups.keys.size

                var overtimeHours = 0.0
                dayGroups.forEach { (_, dayRecs) ->
                    val daySum = dayRecs.sumOf { it.hoursWorked }
                    if (daySum > stdShift) {
                        overtimeHours += (daySum - stdShift)
                    }
                }

                val netTotalHours = dayGroups.values.sumOf { dayRecs ->
                    val daySum = dayRecs.sumOf { it.hoursWorked }
                    if (daySum > 0.5) daySum - 0.5 else 0.0
                }

                val totalHours = if (isFixed) records.sumOf { it.hoursWorked } else netTotalHours
                val regularHours = if (isFixed) (workedDays * stdShift) else netTotalHours
                val estimatedSalary = if (isFixed) {
                    overtimeHours * rate
                } else {
                    netTotalHours * rate
                }


                _reportState.value = ReportState(
                    records = records,
                    totalHours = totalHours,
                    estimatedSalary = estimatedSalary,
                    hourlyRate = rate,
                    periodStart = ps,
                    periodEnd = pe,
                    workedDays = workedDays,
                    isFixedEmployment = isFixed,
                    regularHours = regularHours,
                    overtimeHours = overtimeHours
                )
            }
        }
        viewModelScope.launch {
            val startDay = prefs.payrollStartDay.first()
            val cycleType = prefs.payrollCycleType.first()
            val customStart = prefs.customStartDate.first()
            val customEnd = prefs.customEndDate.first()
            val (ps, pe) = if (start != null && end != null) start to end
                           else PayrollUtil.resolvePeriod(
                               cycleType = cycleType,
                               startDay = startDay,
                               customStart = customStart,
                               customEnd = customEnd,
                               referenceDate = Date()
                           )
            repo.getNotesInRange(ps, pe).collect { notes ->
                _reportState.value = _reportState.value.copy(notes = notes)
            }
        }
    }

    fun exportExcel(onlyCurrentPeriod: Boolean = true) {
        viewModelScope.launch {
            try {
                val accId = _homeState.value.activeAccount?.id ?: repo.getActiveAccountId()
                val p = prefs.allPreferences.first()
                val rate = prefs.getHourlyRate(p, accId)
                val startDay = prefs.getPayrollStartDay(p, accId)
                val cycleType = prefs.getPayrollCycleType(p, accId)
                val customStart = prefs.getCustomStartDate(p, accId)
                val customEnd = prefs.getCustomEndDate(p, accId)
                val isFixed = prefs.getFixedEmployment(p, accId)
                val stdShift = prefs.getStandardShiftHours(p, accId)
                val (ps, pe) = PayrollUtil.resolvePeriod(
                    cycleType = cycleType,
                    startDay = startDay,
                    customStart = customStart,
                    customEnd = customEnd,
                    referenceDate = Date()
                )

                val records = if (onlyCurrentPeriod && ps.isNotBlank() && pe.isNotBlank()) {
                    repo.getRecordsInRangeDirect(ps, pe)
                } else {
                    repo.getAllRecordsDirect()
                }

                if (records.isEmpty()) {
                    showHomeMessage("لا توجد سجلات في هذه الفترة لتصديرها", isError = true)
                    return@launch
                }

                _reportState.value = _reportState.value.copy(isExporting = true)
                val activeAcc = repo.getActiveAccountDirect()
                val empName = activeAcc?.let { it.staffShortName.ifBlank { it.staffName } }
                    ?: _homeState.value.employeeName.ifBlank { prefs.employeeName.first() }

                val result = ExportUtil.exportExcel(
                    context = context,
                    records = records,
                    notes = repo.getAllNotesDirect(),
                    periodStart = ps,
                    periodEnd = pe,
                    hourlyRate = rate,
                    isFixedEmployment = isFixed,
                    standardShiftHours = stdShift,
                    employeeName = empName
                )
                _reportState.value = _reportState.value.copy(isExporting = false)

                if (result.isSuccess) {
                    val path = result.getOrThrow()
                    showHomeMessage("تم تصدير ملف الإكسل بنجاح ✓\nتم الحفظ في: $path")
                    ExportUtil.shareExportedFile(context, path)
                } else {
                    showHomeMessage("فشل تصدير الإكسل: ${result.exceptionOrNull()?.message}", isError = true)
                }
            } catch (e: Exception) {
                showHomeMessage("خطأ أثناء التصدير: ${e.message}", isError = true)
            }
        }
    }

    fun exportCsv() {
        exportExcel()
    }

    // ─── JSON Backup & Restore ────────────────────────────────────────────────

    fun createBackup() {
        viewModelScope.launch {
            try {
                val records = repo.getAllRecordsDirect()
                val notes = repo.getAllNotesDirect()
                val prefsMap = prefs.getAllPrefsMap()
                val accounts = repo.getAllAccountsDirect()

                val json = BackupUtil.buildBackupJson(records, notes, prefsMap, accounts)
                val saveRes = BackupUtil.saveBackupToFile(context, json)
                if (saveRes.isSuccess) {
                    val path = saveRes.getOrThrow()
                    showHomeMessage("تم حفظ النسخة الاحتياطية بنجاح ✓\nالمسار: $path")
                } else {
                    showHomeMessage("فشل حفظ النسخة الاحتياطية: ${saveRes.exceptionOrNull()?.message}", isError = true)
                }
            } catch (e: Exception) {
                showHomeMessage("خطأ أثناء إنشاء النسخة الاحتياطية: ${e.message}", isError = true)
            }
        }
    }

    fun restoreBackupFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    showHomeMessage("تعذر فتح ملف النسخة الاحتياطية", isError = true)
                    return@launch
                }
                val jsonString = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val parsedRes = BackupUtil.parseBackupJson(jsonString)
                if (parsedRes.isFailure) {
                    showHomeMessage("فشل قراءة النسخة الاحتياطية: ${parsedRes.exceptionOrNull()?.message}", isError = true)
                    return@launch
                }
                val backup = parsedRes.getOrThrow()
                // Restore Room entities
                repo.restoreData(backup.records, backup.notes)
                // Restore accounts if present
                if (backup.accounts.isNotEmpty()) {
                    repo.restoreAccounts(backup.accounts)
                }
                // Restore preferences & credentials
                if (backup.preferences.isNotEmpty()) {
                    prefs.restorePrefsFromMap(backup.preferences)
                }
                refreshHome()
                val accMsg = if (backup.accounts.isNotEmpty()) " و ${backup.accounts.size} حساب" else ""
                showHomeMessage("تمت استعادة النسخة الاحتياطية بنجاح ✓\nتم استرجاع ${backup.records.size} وردية$accMsg")
            } catch (e: Exception) {
                showHomeMessage("خطأ أثناء الاستعادة: ${e.message}", isError = true)
            }
        }
    }

    // ─── Settings ─────────────────────────────────────────────────────────────

    fun setHourlyRate(rate: Double) {
        viewModelScope.launch {
            val accId = _homeState.value.activeAccount?.id ?: repo.getActiveAccountId()
            prefs.setHourlyRate(rate, accId)
            refreshHome()
            loadReport()
        }
    }

    fun setPayrollStartDay(day: Int) {
        viewModelScope.launch {
            val accId = _homeState.value.activeAccount?.id ?: repo.getActiveAccountId()
            prefs.setPayrollStartDay(day, accId)
            refreshHome()
            loadReport()
        }
    }

    fun setPayrollCycleType(type: String) {
        viewModelScope.launch {
            val accId = _homeState.value.activeAccount?.id ?: repo.getActiveAccountId()
            prefs.setPayrollCycleType(type, accId)
            refreshHome()
            loadReport()
        }
    }

    fun setCustomPeriodRange(start: String, end: String) {
        viewModelScope.launch {
            val accId = _homeState.value.activeAccount?.id ?: repo.getActiveAccountId()
            prefs.setCustomPeriodRange(start, end, accId)
            prefs.setPayrollCycleType(PayrollUtil.CYCLE_CUSTOM_RANGE, accId)
            refreshHome()
            loadReport()
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { prefs.setLanguage(lang) }
    }

    fun setThemeMode(mode: String) {
        viewModelScope.launch {
            prefs.setThemeMode(mode)
        }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setDarkMode(enabled) }
    }

    fun setTimeFormat(format: String) {
        viewModelScope.launch {
            prefs.setTimeFormat(format)
            refreshHome()
        }
    }

    fun setEmployeeName(name: String) {
        viewModelScope.launch { prefs.setEmployeeName(name) }
    }

    fun setFixedEmployment(enabled: Boolean) {
        viewModelScope.launch {
            val accId = _homeState.value.activeAccount?.id ?: repo.getActiveAccountId()
            prefs.setFixedEmployment(enabled, accId)
            prefs.setHasChosenEmployment(true, accId)
            refreshHome()
            loadReport()
        }
    }

    fun setStandardShiftHours(hours: Double) {
        viewModelScope.launch {
            val accId = _homeState.value.activeAccount?.id ?: repo.getActiveAccountId()
            prefs.setStandardShiftHours(hours, accId)
            refreshHome()
            loadReport()
        }
    }

    // ─── ESS ──────────────────────────────────────────────────────────────────

    fun essLogin(
        email: String,
        pass: String,
        saveCredentials: Boolean,
        onResult: (Result<List<EssInstance>>) -> Unit
    ) {
        viewModelScope.launch {
            val result = repo.essLogin(email, pass)
            if (result.isSuccess) {
                prefs.setEssCredentials(email, pass, saveCredentials)
                // If a name was detected from the account, update automatically
                repo.detectedEmployeeName?.let { detected ->
                    if (detected.isNotBlank()) {
                        prefs.setEmployeeName(detected)
                    }
                }
                val instances = result.getOrDefault(emptyList())
                if (instances.size == 1) {
                    repo.essSelectInstance(instances.first())
                    syncFromEss { count ->
                        if (count > 0) showHomeMessage("تم تسجيل الدخول ومزامنة $count وردية بنجاح ✓")
                    }
                }
            }
            _settingsState.value = _settingsState.value.copy(
                essInstances = result.getOrDefault(emptyList()),
                isEssLoggedIn = result.isSuccess
            )
            onResult(result)
        }
    }

    fun essSelectInstance(instance: EssInstance) {
        viewModelScope.launch {
            repo.essSelectInstance(instance)
            repo.detectedEmployeeName?.let { detected ->
                if (detected.isNotBlank()) {
                    prefs.setEmployeeName(detected)
                }
            }
            // Auto-sync immediately upon selecting instance
            syncFromEss { count ->
                if (count > 0) showHomeMessage("تم اختيار الفرع ومزامنة $count وردية بنجاح ✓")
            }
        }
    }

    private var hasSyncedOnLaunch = false

    fun autoLoginAndSyncIfSaved() {
        if (hasSyncedOnLaunch) return
        hasSyncedOnLaunch = true
        viewModelScope.launch {
            try {
                var accounts = repo.getAllAccountsDirect()
                // Legacy migration if DB has no accounts but DataStore has credentials
                if (accounts.isEmpty()) {
                    val saveCreds = prefs.essSaveCredentials.first()
                    val email = prefs.essEmail.first()
                    val pass = prefs.essPassword.first()
                    if (saveCreds && email.isNotBlank() && pass.isNotBlank()) {
                        val deviceId = QrUtil.getDeviceId(context)
                        val res = repo.fetchAndSaveAccount(email.trim(), pass, deviceId)
                        if (res.isSuccess) {
                            accounts = listOf(res.getOrThrow())
                        }
                    }
                }

                if (accounts.isNotEmpty()) {
                    val active = repo.getActiveAccountDirect() ?: accounts.first().also {
                        repo.setActiveAccount(it.id)
                    }

                    // Silent background update of active account data on app launch
                    val refreshRes = repo.refreshAccount(active)
                    val currentActive = refreshRes.getOrDefault(active)
                    val name = currentActive.staffShortName.ifBlank { currentActive.staffName }
                    prefs.setEmployeeName(name)

                    _settingsState.value = _settingsState.value.copy(
                        isEssLoggedIn = true,
                        employeeName = name,
                        essEmail = currentActive.email
                    )
                    refreshHome()

                    // Also sync clocking shifts silently for active account
                    syncFromEss(silent = true) {}
                }
            } catch (_: Exception) {}
        }
    }

    // ─── Account Management & QR Generation ──────────────────────────────────

    fun addNewAccount(
        email: String,
        pass: String,
        onResult: (Result<EssAccount>) -> Unit
    ) {
        viewModelScope.launch {
            val deviceId = QrUtil.getDeviceId(context)
            val result = repo.fetchAndSaveAccount(email.trim(), pass, deviceId)
            if (result.isSuccess) {
                val acc = result.getOrThrow()
                prefs.setEssCredentials(acc.email, acc.password, true)
                val name = acc.staffShortName.ifBlank { acc.staffName }
                prefs.setEmployeeName(name)
                _settingsState.value = _settingsState.value.copy(
                    isEssLoggedIn = true,
                    employeeName = name,
                    essEmail = acc.email
                )
                refreshHome()
                syncFromEss(silent = false) { _ ->
                    showHomeMessage("تم تسجيل الدخول وإضافة حساب ${name} بنجاح ✓")
                }
            }
            onResult(result)
        }
    }

    private var accountSyncJob: Job? = null

    fun switchAccount(account: EssAccount) {
        viewModelScope.launch {
            accountSyncJob?.cancel()
            repo.setActiveAccount(account.id)
            prefs.setEssCredentials(account.email, account.password, true)
            val name = account.staffShortName.ifBlank { account.staffName }
            prefs.setEmployeeName(name)

            val p = prefs.allPreferences.first()
            val accId = account.id
            val rate = prefs.getHourlyRate(p, accId)
            val startDay = prefs.getPayrollStartDay(p, accId)
            val cycleType = prefs.getPayrollCycleType(p, accId)
            val customStart = prefs.getCustomStartDate(p, accId)
            val customEnd = prefs.getCustomEndDate(p, accId)
            val isFixed = prefs.getFixedEmployment(p, accId)
            val stdShift = prefs.getStandardShiftHours(p, accId)
            val hasChosen = prefs.getHasChosenEmployment(p, accId)

            _settingsState.value = _settingsState.value.copy(
                hourlyRate = rate,
                payrollStartDay = startDay,
                payrollCycleType = cycleType,
                customStartDate = customStart,
                customEndDate = customEnd,
                isFixedEmployment = isFixed,
                standardShiftHours = stdShift,
                hasChosenEmployment = hasChosen,
                employeeName = name,
                essEmail = account.email
            )
            _homeState.value = _homeState.value.copy(
                activeAccount = account,
                employeeName = name,
                isFixedEmployment = isFixed,
                standardShiftHours = stdShift,
                hourlyRate = rate,
                hasChosenEmployment = hasChosen
            )
            refreshHome()
            loadReport()
            showHomeMessage("تم التبديل إلى حساب ${name} بنجاح ✓")
            // Silently sync shifts for switched account with debounce to prevent duplicate network calls on rapid clicks
            accountSyncJob = viewModelScope.launch {
                delay(300L)
                syncFromEss(silent = true) {}
            }
        }
    }

    fun deleteAccount(account: EssAccount) {
        viewModelScope.launch {
            repo.deleteAccount(account.id)
            prefs.clearAccountPrefs(account.id)
            val newActive = repo.getActiveAccountDirect()
            if (newActive != null) {
                prefs.setEssCredentials(newActive.email, newActive.password, true)
                val name = newActive.staffShortName.ifBlank { newActive.staffName }
                prefs.setEmployeeName(name)
                val p = prefs.allPreferences.first()
                val accId = newActive.id
                val rate = prefs.getHourlyRate(p, accId)
                val startDay = prefs.getPayrollStartDay(p, accId)
                val cycleType = prefs.getPayrollCycleType(p, accId)
                val customStart = prefs.getCustomStartDate(p, accId)
                val customEnd = prefs.getCustomEndDate(p, accId)
                val isFixed = prefs.getFixedEmployment(p, accId)
                val stdShift = prefs.getStandardShiftHours(p, accId)
                val hasChosen = prefs.getHasChosenEmployment(p, accId)

                _settingsState.value = _settingsState.value.copy(
                    hourlyRate = rate,
                    payrollStartDay = startDay,
                    payrollCycleType = cycleType,
                    customStartDate = customStart,
                    customEndDate = customEnd,
                    isFixedEmployment = isFixed,
                    standardShiftHours = stdShift,
                    hasChosenEmployment = hasChosen,
                    isEssLoggedIn = true,
                    employeeName = name,
                    essEmail = newActive.email
                )
                _homeState.value = _homeState.value.copy(
                    activeAccount = newActive,
                    employeeName = name,
                    isFixedEmployment = isFixed,
                    standardShiftHours = stdShift,
                    hourlyRate = rate,
                    hasChosenEmployment = hasChosen
                )
            } else {
                prefs.setEssCredentials("", "", false)
                prefs.setEmployeeName("")
                _settingsState.value = _settingsState.value.copy(
                    isEssLoggedIn = false,
                    employeeName = "",
                    essEmail = "",
                    essPassword = ""
                )
                _homeState.value = _homeState.value.copy(
                    activeAccount = null,
                    employeeName = ""
                )
            }
            refreshHome()
            loadReport()
            showHomeMessage("تم حذف الحساب بنجاح")
        }
    }

    fun refreshActiveAccount(onDone: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val active = repo.getActiveAccountDirect()
            if (active == null) {
                onDone?.invoke(false)
                return@launch
            }
            val res = repo.refreshAccount(active)
            if (res.isSuccess) {
                val updated = res.getOrThrow()
                val name = updated.staffShortName.ifBlank { updated.staffName }
                prefs.setEmployeeName(name)
                refreshHome()
                syncFromEss {
                    showHomeMessage("تم تحديث بيانات الحساب بنجاح ✓")
                    onDone?.invoke(true)
                }
            } else {
                showHomeMessage("تعذر تحديث البيانات، تم استخدام النسخة المحفوظة", isError = true)
                onDone?.invoke(false)
            }
        }
    }

    fun generateQrPayload(account: EssAccount, event: String, timestamp: Date = Date()): String {
        val deviceId = account.deviceId.ifBlank { QrUtil.getDeviceId(context) }
        return QrUtil.buildAttendanceQrPayload(
            staffId = account.staffId,
            staffName = account.staffName,
            suId = account.suId,
            event = event,
            clockTime = timestamp,
            deviceId = deviceId
        )
    }

    fun generateQrBitmap(payload: String): Bitmap {
        return QrUtil.generateQrBitmap(payload, sizePx = 600, border = 2)
    }

    fun essLogout() {
        viewModelScope.launch {
            repo.essLogout()
            _settingsState.value = _settingsState.value.copy(
                isEssLoggedIn = false,
                essInstances = emptyList()
            )
        }
    }

    fun revertRecordToEss(record: AttendanceRecord, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                if (!repo.isEssLoggedIn) {
                    val saveCreds = prefs.essSaveCredentials.first()
                    val email = prefs.essEmail.first()
                    val pass = prefs.essPassword.first()
                    if (saveCreds && email.isNotBlank() && pass.isNotBlank()) {
                        val loginRes = repo.essLogin(email.trim(), pass)
                        if (loginRes.isSuccess) {
                            val instances = loginRes.getOrDefault(emptyList())
                            if (instances.isNotEmpty()) repo.essSelectInstance(instances.first())
                            _settingsState.value = _settingsState.value.copy(
                                isEssLoggedIn = true,
                                essInstances = instances
                            )
                        }
                    }
                }
                if (!repo.isEssLoggedIn) {
                    onDone(false, "يجب تسجيل الدخول إلى ESS أولاً")
                    return@launch
                }

                val result = repo.revertRecordToEss(record)
                if (result.isSuccess) {
                    refreshHome()
                    loadReport()
                    onDone(true, "تمت استعادة توقيت الوردية من ESS بنجاح ✓")
                } else {
                    onDone(false, result.exceptionOrNull()?.message ?: "فشلت استعادة الوردية من السيرفر")
                }
            } catch (e: Exception) {
                onDone(false, e.message ?: "حدث خطأ أثناء المزامنة")
            }
        }
    }

    fun syncFromEss(silent: Boolean = false, onResult: (Int) -> Unit = {}) {
        viewModelScope.launch {
            if (!silent) {
                _homeState.value = _homeState.value.copy(isLoading = true)
            }

            // Auto-relogin silently if session expired but credentials exist
            if (!repo.isEssLoggedIn) {
                val saveCreds = prefs.essSaveCredentials.first()
                val email = prefs.essEmail.first()
                val pass = prefs.essPassword.first()
                if (saveCreds && email.isNotBlank() && pass.isNotBlank()) {
                    try {
                        val loginRes = repo.essLogin(email.trim(), pass)
                        if (loginRes.isSuccess) {
                            val instances = loginRes.getOrDefault(emptyList())
                            if (instances.isNotEmpty()) {
                                repo.essSelectInstance(instances.first())
                            }
                            _settingsState.value = _settingsState.value.copy(
                                isEssLoggedIn = true,
                                essInstances = instances
                            )
                        }
                    } catch (_: Exception) {}
                }
            } else {
                _settingsState.value = _settingsState.value.copy(isEssLoggedIn = true)
            }

            val cycleType = prefs.payrollCycleType.first()
            val customStart = prefs.customStartDate.first()
            val customEnd = prefs.customEndDate.first()

            val (syncStart, syncEnd) = if (cycleType == PayrollUtil.CYCLE_CUSTOM_RANGE && customStart.isNotBlank() && customEnd.isNotBlank()) {
                val s = PayrollUtil.parseDate(customStart)
                val eCal = Calendar.getInstance()
                eCal.time = PayrollUtil.parseDate(customEnd)
                eCal.set(Calendar.HOUR_OF_DAY, 23)
                eCal.set(Calendar.MINUTE, 59)
                eCal.set(Calendar.SECOND, 59)
                s to eCal.time
            } else {
                // Sync broad window: from start of previous month through end of next month
                val cal = Calendar.getInstance()
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                val s = cal.time

                val calEnd = Calendar.getInstance()
                calEnd.add(Calendar.MONTH, 1)
                calEnd.set(Calendar.DAY_OF_MONTH, calEnd.getActualMaximum(Calendar.DAY_OF_MONTH))
                calEnd.set(Calendar.HOUR_OF_DAY, 23)
                calEnd.set(Calendar.MINUTE, 59)
                calEnd.set(Calendar.SECOND, 59)
                s to calEnd.time
            }

            val result = repo.syncFromEss(syncStart, syncEnd)
            if (!silent) {
                _homeState.value = _homeState.value.copy(isLoading = false)
            }
            if (result.isSuccess) {
                refreshHome()
                onResult(result.getOrDefault(0))
            } else if (!silent) {
                showHomeMessage("فشل المزامنة: ${result.exceptionOrNull()?.message}", true)
            }
        }
    }

    /**
     * Scans the current period and creates OFF (manual) days for any missing dates.
     */
    fun fillMissingDaysAsOff(onResult: (Int) -> Unit = {}) {
        viewModelScope.launch {
            val start = _homeState.value.periodStart
            val end = _homeState.value.periodEnd
            if (start.isNotBlank() && end.isNotBlank()) {
                val count = repo.fillMissingDaysAsOff(start, end)
                refreshHome()
                if (count > 0) {
                    showHomeMessage("تم تسجيل $count يوم إجازة (OFF) مفقود بنجاح ✓")
                } else {
                    showHomeMessage("جميع الأيام مسجلة بالفعل ولا توجد أيام مفقودة")
                }
                onResult(count)
            }
        }
    }


    // ─── Helpers ─────────────────────────────────────────────────────────────

    fun showHomeMessage(text: String, isError: Boolean = false) {
        _homeState.value = _homeState.value.copy(message = UiMessage(text, isError))
    }

    fun clearMessage() {
        _homeState.value = _homeState.value.copy(message = null)
    }

    class Factory(
        private val repo: AttendanceRepository,
        private val prefs: PrefsUtil,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            MainViewModel(repo, prefs, context) as T
    }
}

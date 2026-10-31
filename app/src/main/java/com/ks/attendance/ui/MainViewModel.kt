package com.ks.attendance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.data.local.entity.DayNote
import com.ks.attendance.data.remote.EssInstance
import com.ks.attendance.data.repository.AttendanceRepository
import com.ks.attendance.utils.ExportUtil
import com.ks.attendance.utils.PayrollUtil
import com.ks.attendance.utils.PrefsUtil
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import android.content.Context
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
    val message: UiMessage? = null
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
    val exportedPath: String? = null
)

data class SettingsState(
    val hourlyRate: Double = 30.0,
    val payrollStartDay: Int = 29,
    val payrollCycleType: String = PayrollUtil.CYCLE_29_TO_28,
    val language: String = "ar",
    val darkMode: Boolean = true,
    val timeFormat: String = "12h",
    val essEmail: String = "",
    val essPassword: String = "",
    val essSaveCredentials: Boolean = false,
    val employeeName: String = "",
    val customStartDate: String = "",
    val customEndDate: String = "",
    val isEssLoggedIn: Boolean = false,
    val essInstances: List<EssInstance> = emptyList()
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

    // ─── Report ───────────────────────────────────────────────────────────────

    private val _reportState = MutableStateFlow(ReportState())
    val reportState: StateFlow<ReportState> = _reportState.asStateFlow()

    init {
        loadSettings()
        viewModelScope.launch {
            repo.deduplicateRecords()
            refreshHome()
        }
        viewModelScope.launch {
            repo.getRecordsForDay(today).collect { records ->
                _homeState.value = _homeState.value.copy(todayRecords = records)
            }
        }
        // Keep home metrics in sync whenever records change
        viewModelScope.launch {
            allRecords.collect {
                refreshHome()
            }
        }
        // Auto-sync from ESS on launch if credentials exist
        viewModelScope.launch {
            delay(1500L)
            val saveCreds = prefs.essSaveCredentials.first()
            val email = prefs.essEmail.first()
            val pass = prefs.essPassword.first()
            if (saveCreds && email.isNotBlank() && pass.isNotBlank()) {
                try {
                    if (!repo.isEssLoggedIn) {
                        val loginRes = repo.essLogin(email.trim(), pass)
                        if (loginRes.isSuccess) {
                            val instances = loginRes.getOrDefault(emptyList())
                            if (instances.isNotEmpty()) {
                                repo.essSelectInstance(instances.first())
                            }
                        }
                    }
                    if (repo.isEssLoggedIn) {
                        syncFromEss { count ->
                            if (count > 0) {
                                showHomeMessage("تمت المزامنة التلقائية: $count وردية جديدة")
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            prefs.allPreferences.collect { p ->
                val rate = p[PrefsUtil.KEY_HOURLY_RATE] ?: PrefsUtil.DEFAULT_HOURLY_RATE
                val startDay = p[PrefsUtil.KEY_PAYROLL_START] ?: PrefsUtil.DEFAULT_PAYROLL_START
                val cycleType = p[PrefsUtil.KEY_PAYROLL_CYCLE] ?: PayrollUtil.CYCLE_29_TO_28
                val lang = p[PrefsUtil.KEY_LANGUAGE] ?: PrefsUtil.DEFAULT_LANGUAGE
                val dark = p[PrefsUtil.KEY_DARK_MODE] ?: true
                val timeFmt = p[PrefsUtil.KEY_TIME_FORMAT] ?: PrefsUtil.DEFAULT_TIME_FORMAT
                val email = p[PrefsUtil.KEY_ESS_EMAIL] ?: ""
                val pass = p[PrefsUtil.KEY_ESS_PASSWORD] ?: ""
                val saveCreds = p[PrefsUtil.KEY_ESS_SAVE_CREDS] ?: false
                val name = p[PrefsUtil.KEY_EMPLOYEE_NAME] ?: ""
                val customStart = p[PrefsUtil.KEY_CUSTOM_START_DATE] ?: ""
                val customEnd = p[PrefsUtil.KEY_CUSTOM_END_DATE] ?: ""

                _settingsState.value = SettingsState(
                    hourlyRate = rate,
                    payrollStartDay = startDay,
                    payrollCycleType = cycleType,
                    language = lang,
                    darkMode = dark,
                    timeFormat = timeFmt,
                    essEmail = email,
                    essPassword = pass,
                    essSaveCredentials = saveCreds,
                    isEssLoggedIn = repo.isEssLoggedIn,
                    employeeName = name,
                    customStartDate = customStart,
                    customEndDate = customEnd
                )
                _homeState.value = _homeState.value.copy(
                    employeeName = name,
                    is24Hour = (timeFmt == "24h")
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
            val rate = prefs.hourlyRate.first()
            val startDay = prefs.payrollStartDay.first()
            val cycleType = prefs.payrollCycleType.first()
            val customStart = prefs.customStartDate.first()
            val customEnd = prefs.customEndDate.first()
            val (periodStart, periodEnd) = PayrollUtil.resolvePeriod(
                cycleType = cycleType,
                startDay = startDay,
                customStart = customStart,
                customEnd = customEnd,
                referenceDate = Date()
            )

            val openSession = repo.getOpenSession()
            val todayHours = repo.getTotalHoursInRange(today, today)
            val recordsInRange = repo.getRecordsInRangeDirect(periodStart, periodEnd)
            val monthHours = recordsInRange.sumOf { it.hoursWorked }
            val workedDays = recordsInRange
                .filter { it.hoursWorked > 0.0 }
                .map { it.workDate }
                .distinct()
                .size

            _homeState.value = _homeState.value.copy(
                openSession = openSession,
                periodStart = periodStart,
                periodEnd = periodEnd,
                todayHours = todayHours,
                monthHours = monthHours,
                monthSalary = monthHours * rate,
                workedDays = workedDays
            )
        }
    }

    // ─── Clock In/Out ─────────────────────────────────────────────────────────

    fun clockIn() {
        viewModelScope.launch {
            try {
                val existing = repo.getOpenSession()
                if (existing != null) {
                    showHomeMessage("يوجد جلسة مفتوحة بالفعل منذ ${PayrollUtil.formatTime(existing.checkInTime)}", isError = true)
                    return@launch
                }
                repo.clockIn()
                refreshHome()
                showHomeMessage("تم تسجيل الدخول ✓")
            } catch (e: Exception) {
                showHomeMessage("خطأ: ${e.message}", true)
            }
        }
    }

    fun clockOut() {
        viewModelScope.launch {
            try {
                val open = repo.getOpenSession()
                    ?: run { showHomeMessage("لا توجد جلسة مفتوحة", true); return@launch }
                val updated = repo.clockOut(open)
                refreshHome()
                showHomeMessage("تم تسجيل الانصراف ✓ — ${PayrollUtil.formatHours(updated.hoursWorked)}")
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
            // If the user modified a record synced from server, mark isFromServer = false
            // so subsequent server syncs will never overwrite the user's manual adjustments!
            val userEditedRecord = if (record.isFromServer) record.copy(isFromServer = false) else record
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
            val rate = prefs.hourlyRate.first()
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

            repo.getRecordsInRange(ps, pe).collect { records ->
                val totalHours = records.sumOf { it.hoursWorked }
                val workedDays = records.map { it.workDate }.toSet().size
                _reportState.value = ReportState(
                    records = records,
                    totalHours = totalHours,
                    estimatedSalary = totalHours * rate,
                    hourlyRate = rate,
                    periodStart = ps,
                    periodEnd = pe,
                    workedDays = workedDays
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

    fun exportExcel() {
        viewModelScope.launch {
            try {
                val records = repo.getAllRecordsDirect()
                if (records.isEmpty()) {
                    showHomeMessage("لا توجد سجلات لتصديرها بعد", isError = true)
                    return@launch
                }
                val rate = prefs.hourlyRate.first()
                val startDay = prefs.payrollStartDay.first()
                val cycleType = prefs.payrollCycleType.first()
                val customStart = prefs.customStartDate.first()
                val customEnd = prefs.customEndDate.first()
                val (ps, pe) = PayrollUtil.resolvePeriod(
                    cycleType = cycleType,
                    startDay = startDay,
                    customStart = customStart,
                    customEnd = customEnd,
                    referenceDate = Date()
                )

                _reportState.value = _reportState.value.copy(isExporting = true)
                val result = ExportUtil.exportExcel(
                    context = context,
                    records = records,
                    notes = emptyList(),
                    periodStart = ps,
                    periodEnd = pe,
                    hourlyRate = rate
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

    // ─── Settings ─────────────────────────────────────────────────────────────

    fun setHourlyRate(rate: Double) {
        viewModelScope.launch { prefs.setHourlyRate(rate) }
    }

    fun setPayrollStartDay(day: Int) {
        viewModelScope.launch { prefs.setPayrollStartDay(day) }
    }

    fun setPayrollCycleType(type: String) {
        viewModelScope.launch {
            prefs.setPayrollCycleType(type)
            refreshHome()
            loadReport()
        }
    }

    fun setCustomPeriodRange(start: String, end: String) {
        viewModelScope.launch {
            prefs.setCustomPeriodRange(start, end)
            refreshHome()
            loadReport()
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { prefs.setLanguage(lang) }
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
        }
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

    fun syncFromEss(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            _homeState.value = _homeState.value.copy(isLoading = true)
            val startDay = prefs.payrollStartDay.first()
            val cycleType = prefs.payrollCycleType.first()
            val customStart = prefs.customStartDate.first()
            val customEnd = prefs.customEndDate.first()
            val (ps, pe) = PayrollUtil.resolvePeriod(
                cycleType = cycleType,
                startDay = startDay,
                customStart = customStart,
                customEnd = customEnd,
                referenceDate = Date()
            )
            val result = repo.syncFromEss(
                PayrollUtil.parseDate(ps),
                PayrollUtil.parseDate(pe)
            )
            _homeState.value = _homeState.value.copy(isLoading = false)
            if (result.isSuccess) {
                refreshHome()
                onResult(result.getOrDefault(0))
            } else {
                showHomeMessage("فشل المزامنة: ${result.exceptionOrNull()?.message}", true)
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun showHomeMessage(text: String, isError: Boolean = false) {
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

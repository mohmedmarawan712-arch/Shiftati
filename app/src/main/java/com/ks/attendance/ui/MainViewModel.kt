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
    val payrollStartDay: Int = 1,
    val payrollCycleType: String = PayrollUtil.CYCLE_CURRENT_MONTH,
    val language: String = "ar",
    val darkMode: Boolean = true,
    val essEmail: String = "",
    val essPassword: String = "",
    val essSaveCredentials: Boolean = false,
    val employeeName: String = "",
    val isEssLoggedIn: Boolean = false,
    val essInstances: List<EssInstance> = emptyList()
)

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
        refreshHome()
        observeSettings()
    }

    private fun observeSettings() {
        viewModelScope.launch {
            combine(
                prefs.hourlyRate,
                prefs.payrollStartDay,
                prefs.payrollCycleType,
                prefs.language,
                prefs.darkMode,
                prefs.essEmail,
                prefs.essPassword,
                prefs.essSaveCredentials
            ) { rate, startDay, cycleType, lang, dark, email, pass, saveCreds ->
                SettingsState(
                    hourlyRate = rate,
                    payrollStartDay = startDay,
                    payrollCycleType = cycleType,
                    language = lang,
                    darkMode = dark,
                    essEmail = email,
                    essPassword = pass,
                    essSaveCredentials = saveCreds,
                    isEssLoggedIn = repo.isEssLoggedIn
                )
            }.collect { state ->
                val currentName = _settingsState.value.employeeName
                _settingsState.value = state.copy(employeeName = currentName)
                refreshHome()
            }
        }
        viewModelScope.launch {
            prefs.employeeName.collect { name ->
                _settingsState.value = _settingsState.value.copy(employeeName = name)
                _homeState.value = _homeState.value.copy(employeeName = name)
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
            val (periodStart, periodEnd) = PayrollUtil.resolvePeriod(cycleType, startDay, Date())

            val openSession = repo.getOpenSession()
            val todayHours = repo.getTotalHoursInRange(today, today)
            val monthHours = repo.getTotalHoursInRange(periodStart, periodEnd)

            _homeState.value = _homeState.value.copy(
                openSession = openSession,
                periodStart = periodStart,
                periodEnd = periodEnd,
                todayHours = todayHours,
                monthHours = monthHours,
                monthSalary = monthHours * rate
            )
        }
        viewModelScope.launch {
            repo.getRecordsForDay(today).collect { records ->
                _homeState.value = _homeState.value.copy(todayRecords = records)
            }
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
            val (ps, pe) = if (start != null && end != null) start to end
                           else PayrollUtil.resolvePeriod(cycleType, startDay, Date())

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
            val (ps, pe) = if (start != null && end != null) start to end
                           else PayrollUtil.resolvePeriod(cycleType, startDay, Date())
            repo.getNotesInRange(ps, pe).collect { notes ->
                _reportState.value = _reportState.value.copy(notes = notes)
            }
        }
    }

    fun exportCsv() {
        viewModelScope.launch {
            _reportState.value = _reportState.value.copy(isExporting = true)
            val state = _reportState.value
            val result = ExportUtil.exportCsv(
                context = context,
                records = state.records,
                notes = state.notes,
                periodStart = state.periodStart,
                periodEnd = state.periodEnd,
                hourlyRate = state.hourlyRate
            )
            _reportState.value = _reportState.value.copy(
                isExporting = false,
                exportedPath = result.getOrNull()
            )
        }
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
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch { prefs.setLanguage(lang) }
    }

    fun setDarkMode(enabled: Boolean) {
        viewModelScope.launch { prefs.setDarkMode(enabled) }
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

    fun syncFromEss(onResult: (Int) -> Unit) {
        viewModelScope.launch {
            _homeState.value = _homeState.value.copy(isLoading = true)
            val startDay = prefs.payrollStartDay.first()
            val cycleType = prefs.payrollCycleType.first()
            val (ps, pe) = PayrollUtil.resolvePeriod(cycleType, startDay, Date())
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

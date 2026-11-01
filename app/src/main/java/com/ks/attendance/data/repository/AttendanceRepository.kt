package com.ks.attendance.data.repository

import android.content.Context
import com.ks.attendance.data.local.dao.AttendanceDao
import com.ks.attendance.data.local.dao.DayNoteDao
import com.ks.attendance.data.local.dao.EssAccountDao
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.data.local.entity.DayNote
import com.ks.attendance.data.local.entity.EssAccount
import com.ks.attendance.data.remote.ClockingParser
import com.ks.attendance.data.remote.EssApiClient
import com.ks.attendance.data.remote.EssAccountProfile
import com.ks.attendance.data.remote.EssInstance
import com.ks.attendance.utils.PayrollUtil
import com.ks.attendance.utils.PrefsUtil
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class AttendanceRepository(
    private val attendanceDao: AttendanceDao,
    private val dayNoteDao: DayNoteDao,
    private val essAccountDao: EssAccountDao,
    private val essClient: EssApiClient,
    private val context: Context
) {
    private val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    // ─── Attendance CRUD ──────────────────────────────────────────────────────

    fun getAllRecords(): Flow<List<AttendanceRecord>> = attendanceDao.getAllRecords()

    fun getRecordsInRange(start: String, end: String): Flow<List<AttendanceRecord>> =
        attendanceDao.getRecordsInRange(start, end)

    fun getRecordsForDay(date: String): Flow<List<AttendanceRecord>> =
        attendanceDao.getRecordsForDay(date)

    suspend fun insertRecord(record: AttendanceRecord): Long = attendanceDao.insert(record)

    suspend fun updateRecord(record: AttendanceRecord) = attendanceDao.update(record)

    suspend fun deleteRecord(record: AttendanceRecord) = attendanceDao.delete(record)
    suspend fun deleteDay(date: String) = attendanceDao.deleteRecordsByDate(date)

    suspend fun getOpenSession(): AttendanceRecord? = attendanceDao.getOpenSession()

    suspend fun getTotalHoursInRange(start: String, end: String): Double =
        attendanceDao.getTotalHoursInRange(start, end) ?: 0.0

    suspend fun getAllRecordsDirect(): List<AttendanceRecord> = attendanceDao.getAllRecordsDirect()

    suspend fun getRecordById(id: Long): AttendanceRecord? = attendanceDao.getById(id)

    suspend fun getRecordsInRangeDirect(start: String, end: String): List<AttendanceRecord> =
        attendanceDao.getRecordsInRangeDirect(start, end)

    suspend fun getAllNotesDirect(): List<DayNote> = dayNoteDao.getAllNotesDirect()

    suspend fun restoreData(records: List<AttendanceRecord>, notes: List<DayNote>) {
        if (records.isNotEmpty()) attendanceDao.insertAll(records)
        if (notes.isNotEmpty()) dayNoteDao.insertAll(notes)
    }

    // ─── Clock-in / Clock-out shortcuts ──────────────────────────────────────

    suspend fun clockIn(): Long {
        val now = System.currentTimeMillis()
        val date = sdfDate.format(Date(now))
        val record = AttendanceRecord(
            workDate = date,
            checkInTime = now,
            checkOutTime = null,
            source = "manual"
        )
        return attendanceDao.insert(record)
    }

    suspend fun clockOut(openRecord: AttendanceRecord): AttendanceRecord {
        val now = System.currentTimeMillis()
        val hours = (now - openRecord.checkInTime).toDouble() / 3_600_000.0
        val updated = openRecord.copy(checkOutTime = now, hoursWorked = hours)
        attendanceDao.update(updated)
        return updated
    }

    // ─── Notes ───────────────────────────────────────────────────────────────

    fun getNotesForDay(date: String): Flow<List<DayNote>> = dayNoteDao.getNotesForDay(date)

    fun getNotesInRange(start: String, end: String): Flow<List<DayNote>> =
        dayNoteDao.getNotesInRange(start, end)

    suspend fun getDatesWithNotes(start: String, end: String): List<String> =
        dayNoteDao.getDatesWithNotes(start, end)

    suspend fun insertNote(note: DayNote): Long = dayNoteDao.insert(note)

    suspend fun updateNote(note: DayNote) = dayNoteDao.update(note)

    suspend fun deleteNote(note: DayNote) = dayNoteDao.delete(note)

    // ─── ESS Online sync ─────────────────────────────────────────────────────

    suspend fun essLogin(email: String, password: String): Result<List<EssInstance>> =
        essClient.login(email, password)

    suspend fun essSelectInstance(instance: EssInstance): Result<Unit> =
        essClient.selectInstance(instance)

    /**
     * Clears old server records for the period, fetches fresh, converts, and inserts
     * without duplicating any existing user-edited or manual shifts.
     */
    suspend fun syncFromEss(startDate: Date, endDate: Date): Result<Int> = runCatching {
        val clockings = essClient.fetchClockings(startDate, endDate).getOrThrow()
        val (shifts, _) = ClockingParser.pairShifts(clockings)

        val startStr = sdfDate.format(startDate)
        val endStr = sdfDate.format(endDate)
        attendanceDao.deleteServerRecordsInRange(startStr, endStr)

        val existingRecords = attendanceDao.getRecordsInRangeDirect(startStr, endStr)
        var inserted = 0
        for (shift in shifts) {
            if (shift.workDate < startStr || shift.workDate > endStr) continue

            // Avoid re-inserting if user already has a modified or manual shift on this day, or matching shift
            val duplicate = existingRecords.any { existing ->
                (existing.workDate == shift.workDate && !existing.isFromServer) ||
                (existing.workDate == shift.workDate && kotlin.math.abs(existing.checkInTime - shift.start.time) < 30 * 60 * 1000L)
            }
            if (duplicate) continue

            val record = AttendanceRecord(
                workDate = shift.workDate,
                checkInTime = shift.start.time,
                checkOutTime = shift.end.time,
                hoursWorked = shift.hoursWorked,
                source = "ess_online",
                isFromServer = true
            )
            attendanceDao.insert(record)
            inserted++
        }

        // Auto-fill missing days from ESS as OFF (manual) days:
        // Employees do not punch on OFF days, so ESS has no records on those dates.
        if (shifts.isNotEmpty()) {
            val todayStr = sdfDate.format(Date())
            val minDate = shifts.minOf { it.workDate }
            val maxDate = if (shifts.maxOf { it.workDate } < todayStr) shifts.maxOf { it.workDate } else todayStr
            fillMissingDaysAsOff(minDate, maxDate)
        }

        deduplicateRecords()
        inserted
    }

    /**
     * Checks all calendar days between [startStr] and [endStr] (bounded by today).
     * Any day without an attendance record is created as an off day ("off_day") with note "إجازة (OFF)"
     * and isFromServer = false (so it appears as "يدوي").
     */
    suspend fun fillMissingDaysAsOff(startStr: String, endStr: String): Int {
        val todayStr = sdfDate.format(Date())
        val boundedEnd = if (endStr > todayStr) todayStr else endStr
        if (startStr > boundedEnd) return 0

        val allDates = PayrollUtil.getDatesInRange(startStr, boundedEnd)
        val existing = attendanceDao.getRecordsInRangeDirect(startStr, boundedEnd)
        val recordedDates = existing.map { it.workDate }.toSet()
        var added = 0

        for (d in allDates) {
            if (d !in recordedDates) {
                val inMillis = PayrollUtil.dateTimeToMillis(d, 0, 0)
                val offRecord = AttendanceRecord(
                    workDate = d,
                    checkInTime = inMillis,
                    checkOutTime = inMillis,
                    hoursWorked = 0.0,
                    source = "off_day",
                    note = "إجازة (OFF)",
                    isFromServer = false
                )
                attendanceDao.insert(offRecord)
                added++
            }
        }
        return added
    }


    /**
     * Reverts a user-modified record back to official ESS server times
     * for that specific date, while preserving any user notes.
     */
    suspend fun revertRecordToEss(record: AttendanceRecord): Result<AttendanceRecord> = runCatching {
        val cal = Calendar.getInstance()
        val parts = record.workDate.split("-").map { it.toInt() }
        cal.set(parts[0], parts[1] - 1, parts[2], 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.time
        cal.set(parts[0], parts[1] - 1, parts[2], 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val dayEnd = cal.time

        val clockings = essClient.fetchClockings(dayStart, dayEnd).getOrThrow()
        val (shifts, _) = ClockingParser.pairShifts(clockings)

        val matchingShifts = shifts.filter { it.workDate == record.workDate }
        if (matchingShifts.isEmpty()) {
            throw IllegalStateException("لم يتم العثور على وردية مسجلة على السيرفر في تاريخ ${record.workDate}")
        }

        // Pick shift closest to the record's check-in time
        val shift = if (matchingShifts.size == 1) {
            matchingShifts.first()
        } else {
            matchingShifts.minByOrNull { kotlin.math.abs(it.start.time - record.checkInTime) } ?: matchingShifts.first()
        }

        val reverted = record.copy(
            checkInTime = shift.start.time,
            checkOutTime = shift.end.time,
            hoursWorked = shift.hoursWorked,
            isFromServer = true,
            source = "ess_online"
            // Note is preserved
        )
        attendanceDao.update(reverted)
        reverted
    }

    /**
     * Scans all records and deletes any duplicated shifts (same workDate & matching check-in),
     * preserving user-edited or manual records over pure server records.
     */
    suspend fun deduplicateRecords(): Int {
        val all = attendanceDao.getAllRecordsDirect()
        val seen = mutableSetOf<String>()
        var deletedCount = 0
        // Sort so user-edited/manual records take precedence over unedited server records, then newest first
        val sorted = all.sortedWith(
            compareByDescending<AttendanceRecord> { !it.isFromServer }
                .thenByDescending { it.id }
        )
        for (rec in sorted) {
            // Bucket by date and 10-minute checkIn interval to catch identical duplicate shifts
            val bucket = "${rec.workDate}_${rec.checkInTime / 600_000}"
            if (!seen.add(bucket)) {
                attendanceDao.delete(rec)
                deletedCount++
            }
        }
        return deletedCount
    }

    val isEssLoggedIn get() = essClient.isLoggedIn
    val detectedEmployeeName get() = essClient.detectedEmployeeName

    fun essLogout() = essClient.logout()

    // ─── Multi-Account & Offline QR ──────────────────────────────────────────

    fun getAllAccounts(): Flow<List<EssAccount>> = essAccountDao.getAllAccounts()

    suspend fun getAllAccountsDirect(): List<EssAccount> = essAccountDao.getAllAccountsDirect()

    fun getActiveAccount(): Flow<EssAccount?> = essAccountDao.getActiveAccount()

    suspend fun getActiveAccountDirect(): EssAccount? = essAccountDao.getActiveAccountDirect()

    suspend fun setActiveAccount(id: Long) {
        essAccountDao.setActiveAccount(id)
        val active = essAccountDao.getActiveAccountDirect()
        if (active != null) {
            essClient.instanceId = active.instanceId
            essClient.staffId = active.staffId
            essClient.schedulingUnitId = active.suId
            essClient.detectedStaffFullName = active.staffName
            essClient.detectedStaffShortName = active.staffShortName
            essClient.detectedEmployeeName = active.staffShortName.ifBlank { active.staffName }
        }
    }

    suspend fun deleteAccount(id: Long) {
        essAccountDao.deleteById(id)
        val remaining = essAccountDao.getAllAccountsDirect()
        if (remaining.isNotEmpty() && essAccountDao.getActiveAccountDirect() == null) {
            setActiveAccount(remaining.first().id)
        }
    }

    suspend fun saveOrUpdateAccount(
        email: String,
        pass: String,
        profile: EssAccountProfile,
        deviceId: String,
        setAsActive: Boolean = true
    ): EssAccount {
        val existing = essAccountDao.getAccountByEmail(email)
        val account = (existing ?: EssAccount(
            email = email,
            password = pass,
            staffId = profile.staffId,
            staffName = profile.staffName,
            staffShortName = profile.staffShortName,
            suId = profile.suId,
            instanceId = profile.instanceId,
            instanceName = profile.instanceName,
            deviceId = deviceId,
            isActive = setAsActive,
            lastSyncTime = System.currentTimeMillis()
        )).copy(
            password = pass.ifBlank { existing?.password ?: "" },
            staffId = profile.staffId,
            staffName = profile.staffName,
            staffShortName = profile.staffShortName,
            suId = profile.suId,
            instanceId = profile.instanceId,
            instanceName = profile.instanceName,
            deviceId = if (deviceId.isNotBlank()) deviceId else existing?.deviceId ?: "b4cfd84c7a095b7f",
            lastSyncTime = System.currentTimeMillis()
        )

        val id = essAccountDao.insert(account)
        val saved = account.copy(id = if (account.id != 0L) account.id else id)
        if (setAsActive) {
            essAccountDao.setActiveAccount(saved.id)
        }
        return saved
    }

    suspend fun fetchAndSaveAccount(
        email: String,
        pass: String,
        deviceId: String,
        preferredInstanceId: String? = null
    ): Result<EssAccount> = runCatching {
        val profile = essClient.fetchFullProfile(email, pass, preferredInstanceId).getOrThrow()
        saveOrUpdateAccount(email, pass, profile, deviceId, setAsActive = true)
    }

    suspend fun refreshAccount(account: EssAccount): Result<EssAccount> = runCatching {
        if (account.password.isBlank()) {
            throw Exception("لا توجد كلمة مرور محفوظة لهذا الحساب")
        }
        val profile = essClient.fetchFullProfile(account.email, account.password, account.instanceId).getOrThrow()
        saveOrUpdateAccount(account.email, account.password, profile, account.deviceId, setAsActive = account.isActive)
    }
}

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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import com.ks.attendance.data.local.AttendanceDatabase
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalCoroutinesApi::class)
class AttendanceRepository(
    private val attendanceDao: AttendanceDao,
    private val dayNoteDao: DayNoteDao,
    private val essAccountDao: EssAccountDao,
    private val essClient: EssApiClient,
    private val context: Context,
    private val database: AttendanceDatabase? = null
) {
    private val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    suspend fun getActiveAccountId(): Long = essAccountDao.getActiveAccountDirect()?.id ?: 0L

    // ─── Attendance CRUD (Auto-scoped to active account) ──────────────────────

    fun getAllRecords(): Flow<List<AttendanceRecord>> =
        essAccountDao.getActiveAccount().flatMapLatest { acc ->
            attendanceDao.getAllRecords(acc?.id ?: 0L)
        }

    fun getRecordsInRange(start: String, end: String): Flow<List<AttendanceRecord>> =
        essAccountDao.getActiveAccount().flatMapLatest { acc ->
            attendanceDao.getRecordsInRange(start, end, acc?.id ?: 0L)
        }

    fun getRecordsForDay(date: String): Flow<List<AttendanceRecord>> =
        essAccountDao.getActiveAccount().flatMapLatest { acc ->
            attendanceDao.getRecordsForDay(date, acc?.id ?: 0L)
        }

    suspend fun insertRecord(record: AttendanceRecord): Long {
        val accId = if (record.accountId != 0L) record.accountId else getActiveAccountId()
        return attendanceDao.insert(record.copy(accountId = accId))
    }

    suspend fun updateRecord(record: AttendanceRecord) {
        val accId = if (record.accountId != 0L) record.accountId else getActiveAccountId()
        attendanceDao.update(record.copy(accountId = accId))
    }

    suspend fun deleteRecord(record: AttendanceRecord) = attendanceDao.delete(record)
    suspend fun deleteDay(date: String) = attendanceDao.deleteRecordsByDate(date, getActiveAccountId())

    companion object {
        const val MAX_ACTIVE_SHIFT_MILLIS = 16 * 3600 * 1000L // 16 hours
    }

    suspend fun getOpenSession(): AttendanceRecord? {
        val minTime = System.currentTimeMillis() - MAX_ACTIVE_SHIFT_MILLIS
        return attendanceDao.getActiveOpenSession(getActiveAccountId(), minTime)
    }

    suspend fun getPendingMissingCheckouts(): List<AttendanceRecord> {
        val minTime = System.currentTimeMillis() - MAX_ACTIVE_SHIFT_MILLIS
        return attendanceDao.getPendingMissingCheckouts(getActiveAccountId(), minTime)
    }

    fun observeOpenSession(): Flow<AttendanceRecord?> =
        essAccountDao.getActiveAccount().flatMapLatest { acc ->
            val accId = acc?.id ?: 0L
            attendanceDao.getAllRecords(accId).map { records ->
                val minTime = System.currentTimeMillis() - MAX_ACTIVE_SHIFT_MILLIS
                records.firstOrNull { it.checkOutTime == null && it.source != "off_day" && it.checkInTime >= minTime }
            }
        }

    suspend fun getTotalHoursInRange(start: String, end: String): Double =
        attendanceDao.getTotalHoursInRange(start, end, getActiveAccountId()) ?: 0.0

    suspend fun getAllRecordsDirect(accountId: Long? = null): List<AttendanceRecord> =
        attendanceDao.getAllRecordsDirect(accountId ?: getActiveAccountId())

    suspend fun getAllRecordsRawDirect(): List<AttendanceRecord> =
        attendanceDao.getAllRecordsRawDirect()

    suspend fun getRecordById(id: Long): AttendanceRecord? = attendanceDao.getById(id)

    suspend fun getRecordsInRangeDirect(start: String, end: String, accountId: Long? = null): List<AttendanceRecord> =
        attendanceDao.getRecordsInRangeDirect(start, end, accountId ?: getActiveAccountId())

    suspend fun getAllNotesDirect(accountId: Long? = null): List<DayNote> =
        dayNoteDao.getAllNotesDirect(accountId ?: getActiveAccountId())

    suspend fun getAllNotesRawDirect(): List<DayNote> =
        dayNoteDao.getAllNotesRawDirect()

    suspend fun restoreData(records: List<AttendanceRecord>, notes: List<DayNote>) {
        if (records.isNotEmpty()) attendanceDao.insertAll(records)
        if (notes.isNotEmpty()) dayNoteDao.insertAll(notes)
    }

    // ─── Clock-in / Clock-out shortcuts ──────────────────────────────────────

    suspend fun clockIn(customTime: Long = System.currentTimeMillis()): Long {
        val date = sdfDate.format(Date(customTime))
        val accId = getActiveAccountId()
        val record = AttendanceRecord(
            workDate = date,
            checkInTime = customTime,
            checkOutTime = null,
            source = "manual",
            accountId = accId
        )
        return attendanceDao.insert(record)
    }

    suspend fun clockOut(openRecord: AttendanceRecord, customTime: Long = System.currentTimeMillis()): AttendanceRecord {
        val outTime = if (customTime >= openRecord.checkInTime) customTime else System.currentTimeMillis()
        val hours = (outTime - openRecord.checkInTime).toDouble() / 3_600_000.0
        val accId = if (openRecord.accountId != 0L) openRecord.accountId else getActiveAccountId()
        val updated = openRecord.copy(checkOutTime = outTime, hoursWorked = hours, accountId = accId)
        attendanceDao.update(updated)
        return updated
    }

    // ─── Notes ───────────────────────────────────────────────────────────────

    fun getNotesForDay(date: String): Flow<List<DayNote>> =
        essAccountDao.getActiveAccount().flatMapLatest { acc ->
            dayNoteDao.getNotesForDay(date, acc?.id ?: 0L)
        }

    fun getNotesInRange(start: String, end: String): Flow<List<DayNote>> =
        essAccountDao.getActiveAccount().flatMapLatest { acc ->
            dayNoteDao.getNotesInRange(start, end, acc?.id ?: 0L)
        }

    suspend fun getDatesWithNotes(start: String, end: String): List<String> =
        dayNoteDao.getDatesWithNotes(start, end, getActiveAccountId())

    suspend fun insertNote(note: DayNote): Long {
        val accId = if (note.accountId != 0L) note.accountId else getActiveAccountId()
        return dayNoteDao.insert(note.copy(accountId = accId))
    }

    suspend fun updateNote(note: DayNote) {
        val accId = if (note.accountId != 0L) note.accountId else getActiveAccountId()
        dayNoteDao.update(note.copy(accountId = accId))
    }

    suspend fun deleteNote(note: DayNote) = dayNoteDao.delete(note)

    // ─── ESS Online sync ─────────────────────────────────────────────────────

    suspend fun essLogin(email: String, password: String): Result<List<EssInstance>> =
        essClient.login(email, password)

    suspend fun essSelectInstance(instance: EssInstance): Result<Unit> =
        essClient.selectInstance(instance)

    /**
     * Clears old server records for the period and active account, fetches fresh, converts, and inserts
     * without duplicating any existing user-edited or manual shifts.
     */
    suspend fun syncFromEss(startDate: Date, endDate: Date, accountId: Long? = null): Result<Int> = runCatching {
        val accId = accountId ?: getActiveAccountId()
        // 1. Fetch clockings from ESS network and parse in memory FIRST.
        // Existing cached records remain visible in the UI without interruption.
        val clockings = essClient.fetchClockings(startDate, endDate).getOrThrow()
        val (shifts, unmatched) = ClockingParser.pairShifts(clockings)

        val startStr = sdfDate.format(startDate)
        val endStr = sdfDate.format(endDate)
        val todayStr = sdfDate.format(Date())

        val shiftDates = shifts.map { it.workDate }.distinct()
        val unmatchedDates = unmatched.map { sdfDate.format(it.time) }.distinct()
        val activeDates = (shiftDates + unmatchedDates + listOf(todayStr)).distinct()

        val existingRecords = attendanceDao.getRecordsInRangeDirect(startStr, endStr, accId)
        val manualRecords = existingRecords.filter { !it.isFromServer && it.source != "off_day" }
        val existingOffDates = existingRecords.filter { it.source == "off_day" }.map { it.workDate }.toSet()
        val toInsert = mutableListOf<AttendanceRecord>()

        // 2. Prepare valid server shifts
        for (shift in shifts) {
            if (shift.workDate < startStr || shift.workDate > endStr) continue

            // Only avoid inserting if user has an existing manual or edited shift on that day/time
            val duplicateWithManual = manualRecords.any { manual ->
                manual.workDate == shift.workDate &&
                kotlin.math.abs(manual.checkInTime - shift.start.time) < 30 * 60 * 1000L
            }
            if (duplicateWithManual) continue

            // Preserve any existing user note written on this server shift
            val existingNote = existingRecords.find {
                it.isFromServer && it.workDate == shift.workDate &&
                kotlin.math.abs(it.checkInTime - shift.start.time) < 30 * 60 * 1000L &&
                it.note.isNotBlank()
            }?.note ?: ""

            toInsert.add(
                AttendanceRecord(
                    workDate = shift.workDate,
                    checkInTime = shift.start.time,
                    checkOutTime = shift.end.time,
                    hoursWorked = shift.hoursWorked,
                    note = existingNote,
                    source = "ess_online",
                    isFromServer = true,
                    accountId = accId
                )
            )
        }

        // 3. Prepare open/unmatched ClockIn punches (both today's active session and earlier unclosed punches)
        val clockIns = unmatched.filter { it.type.startsWith("ClockIn") }
        for (openClockIn in clockIns) {
            val openWorkDate = sdfDate.format(openClockIn.time)
            if (openWorkDate in startStr..endStr) {
                val hasManual = manualRecords.any { manual ->
                    manual.workDate == openWorkDate &&
                    kotlin.math.abs(manual.checkInTime - openClockIn.time.time) < 30 * 60 * 1000L
                }
                if (!hasManual) {
                    val existingNote = existingRecords.find {
                        it.isFromServer && it.workDate == openWorkDate &&
                        kotlin.math.abs(it.checkInTime - openClockIn.time.time) < 30 * 60 * 1000L &&
                        it.note.isNotBlank()
                    }?.note ?: if (openWorkDate == todayStr) "" else "حضور بدون انصراف (سيرفر)"

                    toInsert.add(
                        AttendanceRecord(
                            id = 0,
                            workDate = openWorkDate,
                            checkInTime = openClockIn.time.time,
                            checkOutTime = null,
                            hoursWorked = 0.0,
                            note = existingNote,
                            source = "ess_online",
                            isFromServer = true,
                            accountId = accId
                        )
                    )
                }
            }
        }

        // 4. Prepare missing days up to yesterday as OFF (manual) days in memory
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdfDate.format(cal.time)
        val boundedEnd = if (endStr > yesterdayStr) yesterdayStr else endStr

        val offRecordsToInsert = mutableListOf<AttendanceRecord>()
        if (startStr <= boundedEnd) {
            val allDates = PayrollUtil.getDatesInRange(startStr, boundedEnd)
            val coveredDates = (manualRecords.map { it.workDate } + toInsert.map { it.workDate } + existingOffDates).toSet()
            for (d in allDates) {
                if (d !in coveredDates) {
                    val inMillis = PayrollUtil.dateTimeToMillis(d, 0, 0)
                    offRecordsToInsert.add(
                        AttendanceRecord(
                            workDate = d,
                            checkInTime = inMillis,
                            checkOutTime = inMillis,
                            hoursWorked = 0.0,
                            source = "off_day",
                            note = "إجازة (OFF)",
                            isFromServer = false,
                            accountId = accId
                        )
                    )
                }
            }
        }

        // 5. Apply all database changes atomically in a single transaction.
        // Room emits to the UI exactly once, eliminating screen flickering or empty reloads!
        val db = database ?: AttendanceDatabase.getInstance(context)
        db.withTransaction {
            attendanceDao.deleteServerRecordsInRange(startStr, endStr, accId)
            if (activeDates.isNotEmpty()) {
                attendanceDao.deleteOffDaysOnDates(activeDates, accId)
            }
            if (toInsert.isNotEmpty()) {
                attendanceDao.insertAll(toInsert)
            }
            if (offRecordsToInsert.isNotEmpty()) {
                attendanceDao.insertAll(offRecordsToInsert)
            }
            deduplicateRecords(accId)
        }

        toInsert.size
    }

    /**
     * Checks calendar days between [startStr] and [endStr] (strictly bounded by yesterday).
     * Any past day without an attendance record is created as an off day ("off_day") with note "إجازة (OFF)".
     * Today is never auto-filled as OFF because the working day is still in progress and scanning/punching is expected.
     */
    suspend fun fillMissingDaysAsOff(startStr: String, endStr: String, accountId: Long? = null): Int {
        val accId = accountId ?: getActiveAccountId()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayStr = sdfDate.format(cal.time)
        val boundedEnd = if (endStr > yesterdayStr) yesterdayStr else endStr
        if (startStr > boundedEnd) return 0

        val allDates = PayrollUtil.getDatesInRange(startStr, boundedEnd)
        val existing = attendanceDao.getRecordsInRangeDirect(startStr, boundedEnd, accId)
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
                    isFromServer = false,
                    accountId = accId
                )
                attendanceDao.insert(offRecord)
                added++
            }
        }
        return added
    }

    /**
     * Cleans up any placeholder OFF record mistakenly created on today's date,
     * so that today is never falsely marked as a day off while the day is still active.
     */
    suspend fun cleanPlaceholderTodayOffDay(accountId: Long? = null) {
        val accId = accountId ?: getActiveAccountId()
        val todayStr = sdfDate.format(Date())
        attendanceDao.deleteOffDaysOnDates(listOf(todayStr), accId)
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
            // Note and accountId are preserved
        )
        attendanceDao.update(reverted)
        reverted
    }

    /**
     * Scans records for [accountId] and deletes any duplicated shifts (same workDate & matching check-in),
     * preserving user-edited or manual records over pure server records.
     */
    suspend fun deduplicateRecords(accountId: Long? = null): Int {
        val accId = accountId ?: getActiveAccountId()
        val all = attendanceDao.getAllRecordsDirect(accId)
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
        attendanceDao.deleteRecordsByAccount(id)
        dayNoteDao.deleteNotesByAccount(id)
        essAccountDao.deleteById(id)
        try {
            PrefsUtil(context).clearAccountPrefs(id)
        } catch (_: Exception) {}
        val remaining = essAccountDao.getAllAccountsDirect()
        if (remaining.isNotEmpty()) {
            if (essAccountDao.getActiveAccountDirect() == null) {
                setActiveAccount(remaining.first().id)
            }
        } else {
            essAccountDao.clearActiveFlag()
            essClient.instanceId = null
            essClient.staffId = null
            essClient.schedulingUnitId = null
            essClient.detectedStaffFullName = null
            essClient.detectedStaffShortName = null
            essClient.detectedEmployeeName = null
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

    suspend fun restoreAccounts(accounts: List<EssAccount>) {
        accounts.forEach { acc ->
            essAccountDao.insert(acc)
        }
        val active = essAccountDao.getActiveAccountDirect()
        if (active != null) {
            setActiveAccount(active.id)
        } else if (accounts.isNotEmpty()) {
            setActiveAccount(accounts.first().id)
        }
    }

    suspend fun refreshAccount(account: EssAccount): Result<EssAccount> = runCatching {
        if (account.password.isBlank()) {
            throw Exception("لا توجد كلمة مرور محفوظة لهذا الحساب")
        }
        val profile = essClient.fetchFullProfile(account.email, account.password, account.instanceId).getOrThrow()
        saveOrUpdateAccount(account.email, account.password, profile, account.deviceId, setAsActive = account.isActive)
    }
}

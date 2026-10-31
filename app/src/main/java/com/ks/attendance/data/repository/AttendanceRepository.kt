package com.ks.attendance.data.repository

import android.content.Context
import com.ks.attendance.data.local.dao.AttendanceDao
import com.ks.attendance.data.local.dao.DayNoteDao
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.data.local.entity.DayNote
import com.ks.attendance.data.remote.ClockingParser
import com.ks.attendance.data.remote.EssApiClient
import com.ks.attendance.data.remote.EssInstance
import com.ks.attendance.utils.PayrollUtil
import com.ks.attendance.utils.PrefsUtil
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class AttendanceRepository(
    private val attendanceDao: AttendanceDao,
    private val dayNoteDao: DayNoteDao,
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

    suspend fun getOpenSession(): AttendanceRecord? = attendanceDao.getOpenSession()

    suspend fun getTotalHoursInRange(start: String, end: String): Double =
        attendanceDao.getTotalHoursInRange(start, end) ?: 0.0

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
     * Sync a given payroll period from ESS.
     * Clears old server records for the period, fetches fresh, converts, and inserts.
     */
    suspend fun syncFromEss(startDate: Date, endDate: Date): Result<Int> = runCatching {
        val clockings = essClient.fetchClockings(startDate, endDate).getOrThrow()
        val (shifts, _) = ClockingParser.pairShifts(clockings)

        val startStr = sdfDate.format(startDate)
        val endStr = sdfDate.format(endDate)
        attendanceDao.deleteServerRecordsInRange(startStr, endStr)

        var inserted = 0
        for (shift in shifts) {
            if (shift.workDate < startStr || shift.workDate > endStr) continue
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
        inserted
    }

    val isEssLoggedIn get() = essClient.isLoggedIn
}

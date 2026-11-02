package com.ks.attendance.data.local.dao

import androidx.room.*
import com.ks.attendance.data.local.entity.AttendanceRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AttendanceDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AttendanceRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<AttendanceRecord>)

    @Update
    suspend fun update(record: AttendanceRecord)

    @Delete
    suspend fun delete(record: AttendanceRecord)

    @Query("SELECT * FROM attendance_records WHERE accountId = :accountId ORDER BY workDate DESC, checkInTime DESC")
    fun getAllRecords(accountId: Long): Flow<List<AttendanceRecord>>

    @Query("""
        SELECT * FROM attendance_records 
        WHERE accountId = :accountId AND workDate >= :startDate AND workDate <= :endDate 
        ORDER BY workDate DESC, checkInTime DESC
    """)
    fun getRecordsInRange(startDate: String, endDate: String, accountId: Long): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE accountId = :accountId AND workDate = :date ORDER BY checkInTime ASC")
    fun getRecordsForDay(date: String, accountId: Long): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE id = :id")
    suspend fun getById(id: Long): AttendanceRecord?

    /** Find any open session (checkOutTime IS NULL) for account — used for active clock-in detection */
    @Query("SELECT * FROM attendance_records WHERE accountId = :accountId AND checkOutTime IS NULL AND source != 'off_day' ORDER BY checkInTime DESC LIMIT 1")
    suspend fun getOpenSession(accountId: Long): AttendanceRecord?

    /** Reactive Flow for active clock-in session of an account */
    @Query("SELECT * FROM attendance_records WHERE accountId = :accountId AND checkOutTime IS NULL AND source != 'off_day' ORDER BY checkInTime DESC LIMIT 1")
    fun observeOpenSession(accountId: Long): Flow<AttendanceRecord?>

    @Query("""
        SELECT SUM(hoursWorked) FROM attendance_records 
        WHERE accountId = :accountId AND workDate >= :startDate AND workDate <= :endDate
    """)
    suspend fun getTotalHoursInRange(startDate: String, endDate: String, accountId: Long): Double?

    @Query("DELETE FROM attendance_records WHERE accountId = :accountId AND workDate >= :startDate AND workDate <= :endDate AND isFromServer = 1")
    suspend fun deleteServerRecordsInRange(startDate: String, endDate: String, accountId: Long)

    @Query("DELETE FROM attendance_records WHERE accountId = :accountId AND source = 'off_day' AND workDate IN (:dates)")
    suspend fun deleteOffDaysOnDates(dates: List<String>, accountId: Long)

    @Query("DELETE FROM attendance_records WHERE accountId = :accountId AND workDate = :date")
    suspend fun deleteRecordsByDate(date: String, accountId: Long)

    @Query("DELETE FROM attendance_records WHERE accountId = :accountId")
    suspend fun deleteRecordsByAccount(accountId: Long)

    @Query("SELECT * FROM attendance_records WHERE accountId = :accountId ORDER BY workDate DESC, checkInTime DESC")
    suspend fun getAllRecordsDirect(accountId: Long): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records ORDER BY workDate DESC, checkInTime DESC")
    suspend fun getAllRecordsRawDirect(): List<AttendanceRecord>

    @Query("""
        SELECT * FROM attendance_records 
        WHERE accountId = :accountId AND workDate >= :startDate AND workDate <= :endDate 
        ORDER BY workDate ASC, checkInTime ASC
    """)
    suspend fun getRecordsInRangeDirect(startDate: String, endDate: String, accountId: Long): List<AttendanceRecord>
}

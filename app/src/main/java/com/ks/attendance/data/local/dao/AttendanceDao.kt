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

    @Query("SELECT * FROM attendance_records ORDER BY checkInTime DESC")
    fun getAllRecords(): Flow<List<AttendanceRecord>>

    @Query("""
        SELECT * FROM attendance_records 
        WHERE workDate >= :startDate AND workDate <= :endDate 
        ORDER BY checkInTime ASC
    """)
    fun getRecordsInRange(startDate: String, endDate: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE workDate = :date ORDER BY checkInTime ASC")
    fun getRecordsForDay(date: String): Flow<List<AttendanceRecord>>

    @Query("SELECT * FROM attendance_records WHERE id = :id")
    suspend fun getById(id: Long): AttendanceRecord?

    /** Find any open session (checkOutTime IS NULL) — used for active clock-in detection */
    @Query("SELECT * FROM attendance_records WHERE checkOutTime IS NULL ORDER BY checkInTime DESC LIMIT 1")
    suspend fun getOpenSession(): AttendanceRecord?

    @Query("""
        SELECT SUM(hoursWorked) FROM attendance_records 
        WHERE workDate >= :startDate AND workDate <= :endDate
    """)
    suspend fun getTotalHoursInRange(startDate: String, endDate: String): Double?

    @Query("DELETE FROM attendance_records WHERE workDate >= :startDate AND workDate <= :endDate AND isFromServer = 1")
    suspend fun deleteServerRecordsInRange(startDate: String, endDate: String)

    @Query("DELETE FROM attendance_records WHERE workDate = :date")
    suspend fun deleteRecordsByDate(date: String)

    @Query("SELECT * FROM attendance_records")
    suspend fun getAllRecordsDirect(): List<AttendanceRecord>

    @Query("SELECT * FROM attendance_records WHERE workDate >= :startDate AND workDate <= :endDate")
    suspend fun getRecordsInRangeDirect(startDate: String, endDate: String): List<AttendanceRecord>
}

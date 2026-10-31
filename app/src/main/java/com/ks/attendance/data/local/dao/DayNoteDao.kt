package com.ks.attendance.data.local.dao

import androidx.room.*
import com.ks.attendance.data.local.entity.DayNote
import kotlinx.coroutines.flow.Flow

@Dao
interface DayNoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: DayNote): Long

    @Update
    suspend fun update(note: DayNote)

    @Delete
    suspend fun delete(note: DayNote)

    @Query("SELECT * FROM day_notes WHERE date = :date ORDER BY createdAt ASC")
    fun getNotesForDay(date: String): Flow<List<DayNote>>

    @Query("SELECT * FROM day_notes WHERE date >= :startDate AND date <= :endDate ORDER BY date ASC, createdAt ASC")
    fun getNotesInRange(startDate: String, endDate: String): Flow<List<DayNote>>

    @Query("SELECT DISTINCT date FROM day_notes WHERE date >= :startDate AND date <= :endDate")
    suspend fun getDatesWithNotes(startDate: String, endDate: String): List<String>

    @Query("SELECT * FROM day_notes WHERE id = :id")
    suspend fun getById(id: Long): DayNote?
}

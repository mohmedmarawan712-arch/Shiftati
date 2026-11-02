package com.ks.attendance.data.local.dao

import androidx.room.*
import com.ks.attendance.data.local.entity.DayNote
import kotlinx.coroutines.flow.Flow

@Dao
interface DayNoteDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: DayNote): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<DayNote>)

    @Update
    suspend fun update(note: DayNote)

    @Delete
    suspend fun delete(note: DayNote)

    @Query("SELECT * FROM day_notes WHERE accountId = :accountId AND date = :date ORDER BY createdAt ASC")
    fun getNotesForDay(date: String, accountId: Long): Flow<List<DayNote>>

    @Query("SELECT * FROM day_notes WHERE accountId = :accountId AND date >= :startDate AND date <= :endDate ORDER BY date ASC, createdAt ASC")
    fun getNotesInRange(startDate: String, endDate: String, accountId: Long): Flow<List<DayNote>>

    @Query("SELECT DISTINCT date FROM day_notes WHERE accountId = :accountId AND date >= :startDate AND date <= :endDate")
    suspend fun getDatesWithNotes(startDate: String, endDate: String, accountId: Long): List<String>

    @Query("SELECT * FROM day_notes WHERE id = :id")
    suspend fun getById(id: Long): DayNote?

    @Query("DELETE FROM day_notes WHERE accountId = :accountId")
    suspend fun deleteNotesByAccount(accountId: Long)

    @Query("SELECT * FROM day_notes WHERE accountId = :accountId ORDER BY date ASC, createdAt ASC")
    suspend fun getAllNotesDirect(accountId: Long): List<DayNote>

    @Query("SELECT * FROM day_notes ORDER BY date ASC, createdAt ASC")
    suspend fun getAllNotesRawDirect(): List<DayNote>
}

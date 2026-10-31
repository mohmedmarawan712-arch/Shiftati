package com.ks.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * DayNote — an optional free-text note attached to a calendar day.
 * Multiple notes can exist per day (stored as separate rows).
 */
@Entity(tableName = "day_notes")
data class DayNote(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** "yyyy-MM-dd" */
    val date: String,

    /** Epoch-millis of note creation */
    val createdAt: Long = System.currentTimeMillis(),

    val noteText: String
)

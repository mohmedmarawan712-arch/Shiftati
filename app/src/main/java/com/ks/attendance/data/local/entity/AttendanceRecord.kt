package com.ks.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * AttendanceRecord — one working session (clock-in + clock-out pair).
 * [checkInTime] and [checkOutTime] are stored as epoch-millis (Long).
 */
@Entity(tableName = "attendance_records")
data class AttendanceRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Date string for display/grouping — "yyyy-MM-dd" */
    val workDate: String,

    /** Epoch milliseconds — mandatory */
    val checkInTime: Long,

    /** Epoch milliseconds — nullable when still clocked-in */
    val checkOutTime: Long? = null,

    /** Computed hours (double). Re-computed on save, cached here for queries. */
    val hoursWorked: Double = 0.0,

    /** Free-text note for this session */
    val note: String = "",

    /** "manual" | "ess_online" */
    val source: String = "manual",

    /** Whether this record was synced from ESS and needs no local edit confirmation */
    val isFromServer: Boolean = false
)

package com.ks.attendance.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * EssAccount — persists an ESS employee account offline.
 * Enables generating ClockIn / ClockOut QR codes 100% offline without network connectivity.
 */
@Entity(tableName = "ess_accounts")
data class EssAccount(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val email: String,
    val password: String = "",         // Plain password to allow auto-refresh and session renewal
    val staffId: String,               // e.g. "46741"
    val staffName: String,             // e.g. "Ahmed Marawan Mohamd Mohamd" (full name)
    val staffShortName: String = "",   // e.g. "Ahmed Marawan"
    val suId: String,                  // Scheduling Unit ID e.g. "1480197"
    val instanceId: String,            // e.g. "SSP_EG"
    val instanceName: String = "",     // e.g. "EG SSP"
    val deviceId: String = "",         // e.g. "b4cfd84c7a095b7f"
    val isActive: Boolean = false,
    val lastSyncTime: Long = 0L
)

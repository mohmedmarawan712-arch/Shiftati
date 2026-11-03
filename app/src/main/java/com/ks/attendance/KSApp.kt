package com.ks.attendance

import android.app.Application
import com.ks.attendance.data.local.AttendanceDatabase
import com.ks.attendance.data.remote.EssApiClient
import com.ks.attendance.data.repository.AttendanceRepository
import com.ks.attendance.utils.PrefsUtil

class KSApp : Application() {
    val database by lazy { AttendanceDatabase.getInstance(this) }
    val essClient by lazy { EssApiClient() }
    val repository by lazy {
        AttendanceRepository(
            attendanceDao = database.attendanceDao(),
            dayNoteDao    = database.dayNoteDao(),
            essAccountDao = database.essAccountDao(),
            essClient     = essClient,
            context       = this,
            database      = database
        )
    }
    val prefs by lazy { PrefsUtil(this) }
}

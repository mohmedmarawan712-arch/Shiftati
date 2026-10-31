package com.ks.attendance.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ks.attendance.data.local.dao.AttendanceDao
import com.ks.attendance.data.local.dao.DayNoteDao
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.data.local.entity.DayNote

@Database(
    entities = [AttendanceRecord::class, DayNote::class],
    version = 1,
    exportSchema = false
)
abstract class AttendanceDatabase : RoomDatabase() {

    abstract fun attendanceDao(): AttendanceDao
    abstract fun dayNoteDao(): DayNoteDao

    companion object {
        @Volatile private var INSTANCE: AttendanceDatabase? = null

        fun getInstance(context: Context): AttendanceDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AttendanceDatabase::class.java,
                    "ks_attendance.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

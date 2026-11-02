package com.ks.attendance.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ks.attendance.data.local.dao.AttendanceDao
import com.ks.attendance.data.local.dao.DayNoteDao
import com.ks.attendance.data.local.dao.EssAccountDao
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.data.local.entity.DayNote
import com.ks.attendance.data.local.entity.EssAccount

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [AttendanceRecord::class, DayNote::class, EssAccount::class],
    version = 3,
    exportSchema = false
)
abstract class AttendanceDatabase : RoomDatabase() {

    abstract fun attendanceDao(): AttendanceDao
    abstract fun dayNoteDao(): DayNoteDao
    abstract fun essAccountDao(): EssAccountDao

    companion object {
        @Volatile private var INSTANCE: AttendanceDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE attendance_records ADD COLUMN accountId INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE day_notes ADD COLUMN accountId INTEGER NOT NULL DEFAULT 0")
                // Assign existing records to the currently active account if one exists
                database.execSQL("""
                    UPDATE attendance_records SET accountId = (
                        SELECT id FROM ess_accounts WHERE isActive = 1 LIMIT 1
                    ) WHERE (SELECT COUNT(*) FROM ess_accounts WHERE isActive = 1) > 0
                """)
                database.execSQL("""
                    UPDATE day_notes SET accountId = (
                        SELECT id FROM ess_accounts WHERE isActive = 1 LIMIT 1
                    ) WHERE (SELECT COUNT(*) FROM ess_accounts WHERE isActive = 1) > 0
                """)
            }
        }

        fun getInstance(context: Context): AttendanceDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AttendanceDatabase::class.java,
                    "ks_attendance.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}

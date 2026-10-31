package com.ks.attendance.utils

import android.content.Context
import android.os.Environment
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.data.local.entity.DayNote
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

object ExportUtil {

    private val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val sdfTime = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val sdfDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Export attendance records + notes to a CSV file.
     * Returns the file path on success.
     */
    fun exportCsv(
        context: Context,
        records: List<AttendanceRecord>,
        notes: List<DayNote>,
        periodStart: String,
        periodEnd: String,
        hourlyRate: Double
    ): Result<String> = runCatching {
        val notesByDate = notes.groupBy { it.date }
        val dir = getExportDir(context)
        val fileName = "ks_attendance_${periodStart}_$periodEnd.csv"
        val file = File(dir, fileName)

        FileWriter(file, Charsets.UTF_8).use { fw ->
            // BOM for Excel Arabic support
            fw.write("\uFEFF")
            // Header
            fw.write("التاريخ,يوم الأسبوع,وقت الدخول,وقت الخروج,الساعات,الملاحظات,المصدر\n")

            for (record in records.sortedBy { it.checkInTime }) {
                val date = sdfDate.parse(record.workDate) ?: continue
                val dayName = arabicDayName(date)
                val checkIn = sdfDateTime.format(Date(record.checkInTime))
                val checkOut = record.checkOutTime?.let { sdfDateTime.format(Date(it)) } ?: "—"
                val hours = "%.2f".format(record.hoursWorked)
                val dayNotes = notesByDate[record.workDate]?.joinToString(" | ") { it.noteText } ?: ""
                val source = if (record.source == "ess_online") "أون لاين ESS" else "يدوي"
                fw.write("${record.workDate},$dayName,$checkIn,$checkOut,$hours,\"$dayNotes\",$source\n")
            }

            // Summary row
            val totalHours = records.sumOf { it.hoursWorked }
            val totalSalary = totalHours * hourlyRate
            fw.write("\n")
            fw.write("إجمالي الساعات,,,,${"%.2f".format(totalHours)},,\n")
            fw.write("الراتب المتوقع (EGP),,,,${"%,.0f".format(totalSalary)},,\n")
            fw.write("سعر الساعة (EGP),,,,${"%,.0f".format(hourlyRate)},,\n")
            fw.write("الفترة من,$periodStart,إلى,$periodEnd,,\n")
        }

        file.absolutePath
    }

    private fun getExportDir(context: Context): File {
        val dir = if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "KSAttendance")
        } else {
            File(context.filesDir, "exports")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun arabicDayName(date: Date): String {
        val cal = Calendar.getInstance()
        cal.time = date
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY  -> "السبت"
            Calendar.SUNDAY    -> "الأحد"
            Calendar.MONDAY    -> "الاثنين"
            Calendar.TUESDAY   -> "الثلاثاء"
            Calendar.WEDNESDAY -> "الأربعاء"
            Calendar.THURSDAY  -> "الخميس"
            Calendar.FRIDAY    -> "الجمعة"
            else               -> ""
        }
    }

    fun englishDayName(date: Date): String {
        val cal = Calendar.getInstance()
        cal.time = date
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.SATURDAY  -> "Sat"
            Calendar.SUNDAY    -> "Sun"
            Calendar.MONDAY    -> "Mon"
            Calendar.TUESDAY   -> "Tue"
            Calendar.WEDNESDAY -> "Wed"
            Calendar.THURSDAY  -> "Thu"
            Calendar.FRIDAY    -> "Fri"
            else               -> ""
        }
    }
}

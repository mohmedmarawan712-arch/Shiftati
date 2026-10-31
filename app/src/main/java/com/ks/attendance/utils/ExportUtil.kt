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

    fun exportExcel(
        context: Context,
        records: List<AttendanceRecord>,
        notes: List<DayNote>,
        periodStart: String,
        periodEnd: String,
        hourlyRate: Double
    ): Result<String> = runCatching {
        val notesByDate = notes.groupBy { it.date }
        val dir = getExportDir(context)
        val fileName = "ks_attendance_${periodStart}_$periodEnd.xls"
        val file = File(dir, fileName)

        val totalHours = records.sumOf { it.hoursWorked }
        val totalSalary = totalHours * hourlyRate
        val workedDays = records.map { it.workDate }.distinct().size

        val html = buildString {
            append("""
                <html xmlns:o="urn:schemas-microsoft-com:office:office" 
                      xmlns:x="urn:schemas-microsoft-com:office:excel" 
                      xmlns="http://www.w3.org/TR/REC-html40">
                <head>
                <meta http-equiv="Content-Type" content="text/html; charset=utf-8">
                <!--[if gte mso 9]><xml>
                 <x:ExcelWorkbook>
                  <x:ExcelWorksheets>
                   <x:ExcelWorksheet>
                    <x:Name>تقرير الورديات والراتب</x:Name>
                    <x:WorksheetOptions>
                     <x:DisplayRightToLeft/>
                     <x:Gridline/>
                    </x:WorksheetOptions>
                   </x:ExcelWorksheet>
                  </x:ExcelWorksheets>
                 </x:ExcelWorkbook>
                </xml><![endif]-->
                <style>
                  body { font-family: Tahoma, Arial, sans-serif; direction: rtl; }
                  table { border-collapse: collapse; width: 100%; margin-top: 15px; }
                  th { background-color: #10B981; color: #ffffff; padding: 10px; border: 1px solid #cccccc; font-weight: bold; }
                  td { padding: 8px 12px; border: 1px solid #dddddd; text-align: center; }
                  .title { font-size: 18px; font-weight: bold; color: #004D40; text-align: right; }
                  .summary-box { background-color: #F0FDF4; font-weight: bold; }
                  .header-summary { background-color: #E2E8F0; font-weight: bold; }
                </style>
                </head>
                <body dir="rtl">
                  <div class="title">تقرير ساعات العمل والراتب (شفتاتي)</div>
                  <div>الفترة: من $periodStart إلى $periodEnd</div>
                  <div>تاريخ التصدير: ${sdfDateTime.format(Date())}</div>
                  <br/>
                  <table>
                    <thead>
                      <tr>
                        <th>التاريخ</th>
                        <th>اليوم</th>
                        <th>وقت الحضور</th>
                        <th>وقت الانصراف</th>
                        <th>الساعات</th>
                        <th>المصدر</th>
                        <th>الملاحظات</th>
                      </tr>
                    </thead>
                    <tbody>
            """.trimIndent())

            for (record in records.sortedBy { it.checkInTime }) {
                val date = sdfDate.parse(record.workDate) ?: continue
                val dayName = arabicDayName(date)
                val checkIn = sdfDateTime.format(Date(record.checkInTime))
                val checkOut = record.checkOutTime?.let { sdfDateTime.format(Date(it)) } ?: "مفتوح"
                val hours = "%.2f".format(record.hoursWorked)
                val dayNotes = notesByDate[record.workDate]?.joinToString(" | ") { it.noteText } ?: ""
                val source = when {
                    record.source == "ess_online" && !record.isFromServer -> "معدل"
                    record.source == "ess_online" -> "ESS"
                    else -> "يدوي"
                }
                append("""
                      <tr>
                        <td>${record.workDate}</td>
                        <td>$dayName</td>
                        <td>$checkIn</td>
                        <td>$checkOut</td>
                        <td style="font-weight:bold; color:#047857;">$hours</td>
                        <td>$source</td>
                        <td>$dayNotes</td>
                      </tr>
                """.trimIndent())
            }

            append("""
                    </tbody>
                    <tfoot>
                      <tr class="header-summary">
                        <td colspan="4" style="text-align:right;">إجمالي الساعات المنجزة</td>
                        <td colspan="3" style="font-weight:bold; color:#047857;">${"%.2f".format(totalHours)} ساعة</td>
                      </tr>
                      <tr class="header-summary">
                        <td colspan="4" style="text-align:right;">عدد أيام العمل</td>
                        <td colspan="3">$workedDays يوم</td>
                      </tr>
                      <tr class="header-summary">
                        <td colspan="4" style="text-align:right;">سعر الساعة المحتسب</td>
                        <td colspan="3">${"%,.0f".format(hourlyRate)} ج.م</td>
                      </tr>
                      <tr class="summary-box">
                        <td colspan="4" style="text-align:right; font-size:16px;">صافي الراتب التقديري</td>
                        <td colspan="3" style="font-size:16px; color:#059669;">${"%,.0f".format(totalSalary)} ج.م</td>
                      </tr>
                    </tfoot>
                  </table>
                </body>
                </html>
            """.trimIndent())
        }

        FileWriter(file, Charsets.UTF_8).use { fw ->
            fw.write("\uFEFF")
            fw.write(html)
        }

        file.absolutePath
    }

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
                val source = when {
                    record.source == "ess_online" && !record.isFromServer -> "معدل"
                    record.source == "ess_online" -> "ESS"
                    else -> "يدوي"
                }
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

package com.ks.attendance.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.ks.attendance.data.local.entity.AttendanceRecord
import com.ks.attendance.data.local.entity.DayNote
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

object ExportUtil {

    private val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val sdfTime = SimpleDateFormat("hh:mm a", Locale("ar"))
    private val sdfDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    /**
     * Exports attendance records to a styled Excel XML/HTML (.xls) workbook.
     * Contains columns: اليوم، التاريخ، الحضور، الانصراف، عدد الساعات، المصدر، الملاحظات.
     */
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
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "Shiftati_${periodStart}_${periodEnd}_$timeStamp.xls"
        val file = File(dir, fileName)

        val totalHours = records.sumOf { it.hoursWorked }
        val totalSalary = totalHours * hourlyRate
        val workedDays = records.filter { it.hoursWorked > 0.0 }.map { it.workDate }.distinct().size
        val offDays = records.filter { it.source == "off_day" }.map { it.workDate }.distinct().size

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
                    <x:Name>سجل الحضور والراتب</x:Name>
                    <x:WorksheetOptions>
                     <x:DisplayRightToLeft/>
                     <x:Gridline/>
                    </x:WorksheetOptions>
                   </x:ExcelWorksheet>
                  </x:ExcelWorksheets>
                 </x:ExcelWorkbook>
                </xml><![endif]-->
                <style>
                  body { font-family: 'Segoe UI', Tahoma, Arial, sans-serif; direction: rtl; }
                  table { border-collapse: collapse; width: 100%; margin-top: 12px; }
                  th { background-color: #059669; color: #ffffff; padding: 12px 8px; border: 1px solid #047857; font-weight: bold; font-size: 14px; text-align: center; }
                  td { padding: 10px 8px; border: 1px solid #d1d5db; text-align: center; font-size: 13px; }
                  .title { font-size: 20px; font-weight: bold; color: #065f46; text-align: right; margin-bottom: 6px; }
                  .subtitle { font-size: 13px; color: #4b5563; text-align: right; margin-bottom: 12px; }
                  .summary-row { background-color: #ecfdf5; font-weight: bold; }
                  .total-row { background-color: #d1fae5; font-weight: bold; font-size: 15px; color: #047857; }
                </style>
                </head>
                <body dir="rtl">
                  <div class="title">تقرير ساعات العمل والراتب (شفتاتي)</div>
                  <div class="subtitle">الفترة المحتسبة: من $periodStart إلى $periodEnd &nbsp;|&nbsp; تاريخ التصدير: ${sdfDateTime.format(Date())}</div>
                  <table>
                    <thead>
                      <tr>
                        <th>اليوم</th>
                        <th>التاريخ</th>
                        <th>الحضور</th>
                        <th>الانصراف</th>
                        <th>عدد الساعات</th>
                        <th>المصدر</th>
                        <th>الملاحظات</th>
                      </tr>
                    </thead>
                    <tbody>
            """.trimIndent())

            for (record in records.sortedBy { it.checkInTime }) {
                val date = runCatching { sdfDate.parse(record.workDate) }.getOrNull() ?: Date(record.checkInTime)
                val dayName = arabicDayName(date)
                val isOff = (record.source == "off_day")
                val checkIn = if (isOff) "إجازة (OFF)" else PayrollUtil.formatTime(record.checkInTime, false)
                val checkOut = if (isOff) "-" else (record.checkOutTime?.let { PayrollUtil.formatTime(it, false) } ?: "مفتوح")
                val hoursText = if (isOff) "عطلة (0 س)" else PayrollUtil.formatHours(record.hoursWorked)
                val hoursDec = if (isOff) "0.00" else "%.2f".format(record.hoursWorked)
                val dayNotes = notesByDate[record.workDate]?.joinToString(" | ") { it.noteText } ?: record.note
                val source = when {
                    isOff -> "إجازة"
                    record.source == "ess_online" && !record.isFromServer -> "معدل"
                    record.source == "ess_online" -> "ESS"
                    else -> "يدوي"
                }
                val rowStyle = if (isOff) "background-color: #fffbeb;" else ""
                append("""
                      <tr style="$rowStyle">
                        <td style="font-weight:bold;">$dayName</td>
                        <td>${record.workDate}</td>
                        <td style="color:${if (isOff) "#b45309" else "#059669"}; font-weight:bold;">$checkIn</td>
                        <td style="color:${if (isOff) "#b45309" else "#2563eb"}; font-weight:bold;">$checkOut</td>
                        <td style="font-weight:bold; color:${if (isOff) "#b45309" else "#047857"};">$hoursText ($hoursDec)</td>
                        <td>$source</td>
                        <td style="text-align:right;">$dayNotes</td>
                      </tr>
                """.trimIndent())
            }

            append("""
                    </tbody>
                    <tfoot>
                      <tr class="summary-row">
                        <td colspan="4" style="text-align:right;">إجمالي الساعات المنجزة:</td>
                        <td colspan="3" style="text-align:center; color:#047857;">${"%.2f".format(totalHours)} ساعة</td>
                      </tr>
                      <tr class="summary-row">
                        <td colspan="4" style="text-align:right;">عدد أيام العمل الفعلية:</td>
                        <td colspan="3" style="text-align:center;">$workedDays يوم</td>
                      </tr>
                      <tr class="summary-row">
                        <td colspan="4" style="text-align:right;">عدد أيام الإجازات (OFF):</td>
                        <td colspan="3" style="text-align:center; color:#b45309;">$offDays يوم</td>
                      </tr>
                      <tr class="summary-row">
                        <td colspan="4" style="text-align:right;">سعر الساعة:</td>
                        <td colspan="3" style="text-align:center;">${"%,.0f".format(hourlyRate)} ج.م</td>
                      </tr>
                      <tr class="total-row">
                        <td colspan="4" style="text-align:right;">صافي الراتب التقديري:</td>
                        <td colspan="3" style="text-align:center;">${"%,.0f".format(totalSalary)} ج.م</td>
                      </tr>
                    </tfoot>
                  </table>
                </body>
                </html>
            """.trimIndent())
        }

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                writer.write("\uFEFF") // UTF-8 BOM for Arabic support
                writer.write(html)
                writer.flush()
            }
        }

        file.absolutePath
    }

    private fun getExportDir(context: Context): File {
        // Preferred: Public Downloads folder
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (downloads != null && downloads.exists()) {
            val folder = File(downloads, "Shiftati")
            if (!folder.exists()) folder.mkdirs()
            if (folder.canWrite()) return folder
        }
        // Fallback: App External Documents
        val extDocs = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        if (extDocs != null) {
            if (!extDocs.exists()) extDocs.mkdirs()
            return extDocs
        }
        // Fallback: Internal files
        val internal = File(context.filesDir, "exports")
        if (!internal.exists()) internal.mkdirs()
        return internal
    }

    /**
     * Share or open the exported file using Android system intent with FileProvider
     */
    fun shareExportedFile(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) return

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.ms-excel"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "سجل حضور شفتاتي")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "مشاركة أو فتح ملف الإكسل").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Silently ignore if no app is available or permission issue
        }
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
}

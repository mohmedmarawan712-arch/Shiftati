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
    private val sdfDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

    /**
     * Exports attendance records to a styled Excel XML/HTML (.xls) workbook with live dynamic formulas.
     * Supports both "Hourly" (نظام الساعة) and "Salaried / Overtime" (نظام التعيين).
     */
    fun exportExcel(
        context: Context,
        records: List<AttendanceRecord>,
        notes: List<DayNote>,
        periodStart: String,
        periodEnd: String,
        hourlyRate: Double,
        isFixedEmployment: Boolean = false,
        standardShiftHours: Double = 9.0
    ): Result<String> = runCatching {
        val notesByDate = notes.groupBy { it.date }
        val dir = getExportDir(context)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val systemTag = if (isFixedEmployment) "Salaried" else "Hourly"
        val fileName = "Shiftati_${systemTag}_${periodStart}_${periodEnd}_$timeStamp.xls"
        val file = File(dir, fileName)

        val sortedRecords = records.sortedBy { it.checkInTime }
        val totalHours = sortedRecords.sumOf { it.hoursWorked }
        val workedRecords = sortedRecords.filter { it.hoursWorked > 0.0 }
        val dayGroups = workedRecords.groupBy { it.workDate }
        val workedDays = dayGroups.keys.size
        val offDays = sortedRecords.filter { it.source == "off_day" }.map { it.workDate }.distinct().size

        var overtimeHours = 0.0
        dayGroups.forEach { (_, dayRecs) ->
            val daySum = dayRecs.sumOf { it.hoursWorked }
            if (daySum > standardShiftHours) {
                overtimeHours += (daySum - standardShiftHours)
            }
        }
        val regularHours = if (isFixedEmployment) (workedDays * standardShiftHours) else totalHours
        val totalSalary = if (isFixedEmployment) {
            overtimeHours * hourlyRate
        } else {
            totalHours * hourlyRate
        }

        val firstDataRow = 2
        val lastDataRow = if (sortedRecords.isNotEmpty()) 1 + sortedRecords.size else 2
        val sheetName = if (isFixedEmployment) "تعيين - أوفر تايم" else "ساعات عمل وراتب"

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
                    <x:Name>$sheetName</x:Name>
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
                  th { background-color: #059669; color: #ffffff; padding: 10px 6px; border: 1px solid #047857; font-weight: bold; font-size: 13px; text-align: center; }
                  td { padding: 8px 6px; border: 1px solid #d1d5db; text-align: center; font-size: 12px; }
                  .title { font-size: 18px; font-weight: bold; color: #065f46; text-align: right; margin-bottom: 4px; }
                  .subtitle { font-size: 12px; color: #4b5563; text-align: right; margin-bottom: 10px; }
                  .summary-row { background-color: #f0fdf4; }
                  .lbl { text-align: right; font-weight: 600; color: #374151; padding-right: 12px; }
                  .total-row { background-color: #d1fae5; border-top: 2px solid #059669; }
                  .total-lbl { font-size: 14px; font-weight: bold; color: #047857; text-align: right; padding-right: 12px; }
                  .total-val { font-size: 15px; font-weight: bold; color: #047857; text-align: center; }
                  .font-bold { font-weight: bold; }
                  .num { mso-number-format:"0\.00"; text-align: center; }
                  .currency { mso-number-format:"\#\,\#\#0\.00"; text-align: center; font-weight: 600; }
                  .integer { mso-number-format:"0"; text-align: center; }
                </style>
                </head>
                <body dir="rtl">
                  <div class="title">تقرير ساعات العمل والراتب (شفتاتي)</div>
                  <div class="subtitle">الفترة المحتسبة: من $periodStart إلى $periodEnd &nbsp;|&nbsp; تاريخ التصدير: ${sdfDateTime.format(Date())} &nbsp;|&nbsp; نظام العمل: ${if (isFixedEmployment) "تعيين (احتساب إضافي بعد $standardShiftHours س)" else "ساعة (حسب إجمالي الساعات)"}</div>
                  <table>
                    <thead>
                      <tr>
                        <th>اليوم</th>
                        <th>التاريخ</th>
                        <th>الحضور</th>
                        <th>الانصراف</th>
                        <th>ساعات العمل</th>
                        ${if (isFixedEmployment) "<th>الإضافي (ساعات)</th><th>مستحق الإضافي (ج.م)</th>" else "<th>أجر اليوم (ج.م)</th>"}
                        <th>المصدر</th>
                        <th>الملاحظات</th>
                      </tr>
                    </thead>
                    <tbody>
            """.trimIndent())

            for ((index, record) in sortedRecords.withIndex()) {
                val row = firstDataRow + index
                val date = runCatching { sdfDate.parse(record.workDate) }.getOrNull() ?: Date(record.checkInTime)
                val dayName = arabicDayName(date)
                val isOff = (record.source == "off_day")
                val checkIn = if (isOff) "إجازة (OFF)" else PayrollUtil.formatTime(record.checkInTime, false)
                val checkOut = if (isOff) "-" else (record.checkOutTime?.let { PayrollUtil.formatTime(it, false) } ?: "مفتوح")
                val hoursDec = if (isOff) 0.0 else record.hoursWorked
                val dayNotes = notesByDate[record.workDate]?.joinToString(" | ") { it.noteText } ?: record.note
                val source = when {
                    isOff -> "إجازة"
                    record.source == "ess_online" && !record.isFromServer -> "معدل"
                    record.source == "ess_online" -> "ESS"
                    else -> "يدوي"
                }

                val rowStyle = if (isOff) "background-color: #fffbeb;" else ""

                if (isFixedEmployment) {
                    val otHours = if (!isOff && hoursDec > standardShiftHours) (hoursDec - standardShiftHours) else 0.0
                    val otWage = otHours * hourlyRate
                    append("""
                      <tr style="$rowStyle">
                        <td style="font-weight:bold;">$dayName</td>
                        <td>${record.workDate}</td>
                        <td style="color:${if (isOff) "#b45309" else "#059669"}; font-weight:bold;">$checkIn</td>
                        <td style="color:${if (isOff) "#b45309" else "#2563eb"}; font-weight:bold;">$checkOut</td>
                        <td class="num font-bold" x:num="${"%.2f".format(hoursDec)}">${"%.2f".format(hoursDec)}</td>
                        <td class="num font-bold" style="color:${if (otHours > 0) "#059669" else "#6b7280"};" x:num="${"%.2f".format(otHours)}" x:f="=IF(E$row&gt;$standardShiftHours, ROUND(E$row-$standardShiftHours, 2), 0)">${if (otHours > 0) "+%.2f".format(otHours) else "0.00"}</td>
                        <td class="currency" x:num="${"%.2f".format(otWage)}" x:f="=ROUND(F$row*$hourlyRate, 2)">${"%,.2f".format(otWage)}</td>
                        <td>$source</td>
                        <td style="text-align:right;">$dayNotes</td>
                      </tr>
                    """.trimIndent())
                } else {
                    val dailyWage = hoursDec * hourlyRate
                    append("""
                      <tr style="$rowStyle">
                        <td style="font-weight:bold;">$dayName</td>
                        <td>${record.workDate}</td>
                        <td style="color:${if (isOff) "#b45309" else "#059669"}; font-weight:bold;">$checkIn</td>
                        <td style="color:${if (isOff) "#b45309" else "#2563eb"}; font-weight:bold;">$checkOut</td>
                        <td class="num font-bold" x:num="${"%.2f".format(hoursDec)}">${"%.2f".format(hoursDec)}</td>
                        <td class="currency" x:num="${"%.2f".format(dailyWage)}" x:f="=ROUND(E$row*$hourlyRate, 2)">${"%,.2f".format(dailyWage)}</td>
                        <td>$source</td>
                        <td style="text-align:right;">$dayNotes</td>
                      </tr>
                    """.trimIndent())
                }
            }

            if (isFixedEmployment) {
                append("""
                    </tbody>
                    <tfoot>
                      <tr class="summary-row">
                        <td colspan="4" class="lbl">إجمالي الساعات الفعلية المسجلة:</td>
                        <td class="num font-bold" x:num="${"%.2f".format(totalHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(E$firstDataRow:E$lastDataRow)\"" else ""}>${"%.2f".format(totalHours)} س</td>
                        <td colspan="4"></td>
                      </tr>
                      <tr class="summary-row">
                        <td colspan="5" class="lbl">إجمالي ساعات الإضافي (الأوفر تايم):</td>
                        <td class="num font-bold" style="color:#059669;" x:num="${"%.2f".format(overtimeHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(F$firstDataRow:F$lastDataRow)\"" else ""}>+${"%.2f".format(overtimeHours)} س</td>
                        <td colspan="3"></td>
                      </tr>
                      <tr class="summary-row">
                        <td colspan="4" class="lbl">عدد أيام العمل الفعلية:</td>
                        <td class="integer font-bold" x:num="$workedDays" ${if (sortedRecords.isNotEmpty()) "x:f=\"=COUNTIF(E$firstDataRow:E$lastDataRow, &quot;&gt;0&quot;)\"" else ""}>$workedDays يوم</td>
                        <td colspan="4"></td>
                      </tr>
                      <tr class="summary-row">
                        <td colspan="4" class="lbl">الساعات الأساسية المحتسبة ($workedDays يوم × ${standardShiftHours.toInt()} س):</td>
                        <td class="num font-bold" style="color:#2563eb;" x:num="${"%.2f".format(regularHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=COUNTIF(E$firstDataRow:E$lastDataRow, &quot;&gt;0&quot;)*${standardShiftHours.toInt()}\"" else ""}>${"%.2f".format(regularHours)} س</td>
                        <td colspan="4"></td>
                      </tr>
                      <tr class="summary-row">
                        <td colspan="4" class="lbl">عدد أيام الإجازات (OFF):</td>
                        <td class="integer font-bold" style="color:#b45309;" x:num="$offDays" ${if (sortedRecords.isNotEmpty()) "x:f=\"=COUNTIF(E$firstDataRow:E$lastDataRow, &quot;=0&quot;)\"" else ""}>$offDays يوم</td>
                        <td colspan="4"></td>
                      </tr>
                      <tr class="summary-row">
                        <td colspan="4" class="lbl">سعر ساعة الإضافي:</td>
                        <td class="currency font-bold" x:num="${"%.2f".format(hourlyRate)}">${"%,.0f".format(hourlyRate)} ج.م</td>
                        <td colspan="4"></td>
                      </tr>
                      <tr class="total-row">
                        <td colspan="6" class="total-lbl">إجمالي مستحقات الإضافي (الأوفر تايم):</td>
                        <td colspan="3" class="total-val" x:num="${"%.2f".format(totalSalary)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=ROUND(SUM(G$firstDataRow:G$lastDataRow), 2)\"" else ""}>${"%,.2f".format(totalSalary)} ج.م</td>
                      </tr>
                    </tfoot>
                  </table>
                </body>
                </html>
                """.trimIndent())
            } else {
                append("""
                    </tbody>
                    <tfoot>
                      <tr class="summary-row">
                        <td colspan="4" class="lbl">إجمالي ساعات العمل:</td>
                        <td class="num font-bold" x:num="${"%.2f".format(totalHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(E$firstDataRow:E$lastDataRow)\"" else ""}>${"%.2f".format(totalHours)} س</td>
                        <td colspan="3"></td>
                      </tr>
                      <tr class="summary-row">
                        <td colspan="4" class="lbl">عدد أيام العمل الفعلية:</td>
                        <td class="integer font-bold" x:num="$workedDays" ${if (sortedRecords.isNotEmpty()) "x:f=\"=COUNTIF(E$firstDataRow:E$lastDataRow, &quot;&gt;0&quot;)\"" else ""}>$workedDays يوم</td>
                        <td colspan="3"></td>
                      </tr>
                      <tr class="summary-row">
                        <td colspan="4" class="lbl">عدد أيام الإجازات (OFF):</td>
                        <td class="integer font-bold" style="color:#b45309;" x:num="$offDays" ${if (sortedRecords.isNotEmpty()) "x:f=\"=COUNTIF(E$firstDataRow:E$lastDataRow, &quot;=0&quot;)\"" else ""}>$offDays يوم</td>
                        <td colspan="3"></td>
                      </tr>
                      <tr class="summary-row">
                        <td colspan="4" class="lbl">سعر الساعة التعاقدي:</td>
                        <td class="currency font-bold" x:num="${"%.2f".format(hourlyRate)}">${"%,.0f".format(hourlyRate)} ج.م</td>
                        <td colspan="3"></td>
                      </tr>
                      <tr class="total-row">
                        <td colspan="5" class="total-lbl">صافي الراتب المستحق (التقديري):</td>
                        <td colspan="3" class="total-val" x:num="${"%.2f".format(totalSalary)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=ROUND(SUM(F$firstDataRow:F$lastDataRow), 2)\"" else ""}>${"%,.2f".format(totalSalary)} ج.م</td>
                      </tr>
                    </tfoot>
                  </table>
                </body>
                </html>
                """.trimIndent())
            }
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

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
     * Exports attendance records to a clean, well-proportioned Excel workbook (.xls)
     * Includes the app's final calculation summary card (Total hours & Total money),
     * live Excel formulas, adapted column widths, and 0.5h break deduction for Hourly mode.
     */
    fun exportExcel(
        context: Context,
        records: List<AttendanceRecord>,
        notes: List<DayNote>,
        periodStart: String,
        periodEnd: String,
        hourlyRate: Double,
        isFixedEmployment: Boolean = false,
        standardShiftHours: Double = 9.0,
        employeeName: String = ""
    ): Result<String> = runCatching {
        val notesByDate = notes.groupBy { it.date }
        val dir = getExportDir(context)

        // 1. File Name: First two names of account owner + date
        val rawName = employeeName.trim().ifBlank { "شفتاتي" }
        val nameWords = rawName.split(Regex("\\s+")).filter { it.isNotBlank() }
        val firstTwoNames = when {
            nameWords.size >= 2 -> "${nameWords[0]}_${nameWords[1]}"
            nameWords.isNotEmpty() -> nameWords[0]
            else -> "شفتاتي"
        }
        val safeName = firstTwoNames.replace(Regex("[/\\\\:*?\"<>|]"), "")
        val datePart = if (periodStart.isNotBlank() && periodEnd.isNotBlank()) {
            "${periodStart}_${periodEnd}"
        } else {
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        }
        val fileName = "${safeName}_${datePart}.xls"
        val file = File(dir, fileName)

        // 2. Metrics Calculation (Ordered strictly by date ASC then time ASC)
        val sortedRecords = records.sortedWith(compareBy<AttendanceRecord> { it.workDate }.thenBy { it.checkInTime })
        val workedRecords = sortedRecords.filter { it.hoursWorked > 0.0 && it.source != "off_day" }
        val workedDays = workedRecords.groupBy { it.workDate }.keys.size
        val offDays = sortedRecords.filter { it.source == "off_day" }.map { it.workDate }.distinct().size

        val totalRawHours = sortedRecords.sumOf { if (it.source == "off_day") 0.0 else it.hoursWorked }

        // Hourly: 0.5h break per worked day (> 0.5h)
        val breakHoursPerRecord = sortedRecords.map { rec ->
            if (rec.source != "off_day" && rec.hoursWorked > 0.5) 0.5 else 0.0
        }
        val totalBreakHours = breakHoursPerRecord.sum()
        val totalNetHours = sortedRecords.mapIndexed { idx, rec ->
            val h = if (rec.source == "off_day") 0.0 else rec.hoursWorked
            maxOf(0.0, h - breakHoursPerRecord[idx])
        }.sum()
        val totalHourlySalary = totalNetHours * hourlyRate

        // Salaried: Overtime exceeding standardShiftHours (e.g. 9.0)
        var totalOvertimeHours = 0.0
        sortedRecords.forEach { rec ->
            if (rec.source != "off_day" && rec.hoursWorked > standardShiftHours) {
                totalOvertimeHours += (rec.hoursWorked - standardShiftHours)
            }
        }
        val totalSalariedOvertimePay = totalOvertimeHours * hourlyRate
        val totalRegularHours = workedDays * standardShiftHours

        // Row indices in Excel:
        // Row 1: Title
        // Row 2: Metadata
        // Row 3: Space
        // Row 4: Summary Card Headers
        // Row 5: Summary Card Values
        // Row 6: Space
        // Row 7: Table Headers
        // Rows 8 to (7 + sortedRecords.size): Data rows
        val firstDataRow = 8
        val lastDataRow = if (sortedRecords.isNotEmpty()) 7 + sortedRecords.size else 8
        val sheetName = if (isFixedEmployment) "نظام التعيين" else "نظام الساعة"

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
                  table { border-collapse: collapse; margin-top: 6px; }
                  th { background-color: #059669; color: #ffffff; padding: 8px 10px; border: 1px solid #047857; font-weight: bold; font-size: 11pt; text-align: center; white-space: nowrap; }
                  td { padding: 7px 10px; border: 1px solid #cbd5e1; text-align: center; font-size: 10pt; white-space: nowrap; }
                  .th-calc { background-color: #047857; color: #fef08a; border: 1px solid #065f46; font-weight: bold; }
                  .title-cell { font-size: 16pt; font-weight: bold; color: #065f46; text-align: right; border: none; padding: 6px 8px; }
                  .meta-cell { font-size: 10pt; color: #475569; text-align: right; border: none; padding: 2px 8px 12px 8px; }
                  .card-hdr { background-color: #ecfdf5; color: #065f46; font-weight: bold; font-size: 11pt; border: 1px solid #a7f3d0; text-align: center; padding: 6px; }
                  .card-val { background-color: #f0fdf4; color: #047857; font-weight: bold; font-size: 14pt; border: 1px solid #a7f3d0; text-align: center; padding: 8px; }
                  .summary-row { background-color: #f1f5f9; font-weight: bold; }
                  .summary-lbl { text-align: right; font-weight: bold; color: #334155; padding-right: 12px; }
                  .total-bar { background-color: #d1fae5; border-top: 2px solid #059669; font-weight: bold; }
                  .total-lbl { font-size: 11pt; font-weight: bold; color: #047857; text-align: right; padding-right: 12px; }
                  .total-val { font-size: 13pt; font-weight: bold; color: #047857; text-align: center; }
                  .font-bold { font-weight: bold; }
                  .num { mso-number-format:"0\.00"; text-align: center; }
                  .currency { mso-number-format:"\#\,\#\#0\.00"; text-align: center; font-weight: 700; }
                  .integer { mso-number-format:"0"; text-align: center; }
                </style>
                </head>
                <body dir="rtl">
                  <table border="1" cellpadding="6" cellspacing="0">
                    <colgroup>
                      <col width="80" style="width: 60pt;">
                      <col width="100" style="width: 75pt;">
                      <col width="85" style="width: 65pt;">
                      <col width="85" style="width: 65pt;">
                      <col width="80" style="width: 60pt;">
                      <col width="70" style="width: 55pt;">
                      <col width="150" style="width: 115pt;">
                      <col width="80" style="width: 60pt;">
                      <col width="90" style="width: 70pt;">
                      <col width="115" style="width: 90pt;">
                    </colgroup>
                    <thead>
                      <!-- Row 1: Title -->
                      <tr>
                        <td colspan="10" class="title-cell">تقرير ساعات العمل والراتب (شفتاتي)</td>
                      </tr>
                      <!-- Row 2: Metadata -->
                      <tr>
                        <td colspan="10" class="meta-cell">
                          <b>الموظف:</b> $rawName &nbsp;|&nbsp;
                          <b>الفترة:</b> من $periodStart إلى $periodEnd &nbsp;|&nbsp;
                          <b>تاريخ التصدير:</b> ${sdfDateTime.format(Date())} &nbsp;|&nbsp;
                          <b>نظام العمل:</b> ${if (isFixedEmployment) "نظام التعيين" else "نظام الساعة"}
                        </td>
                      </tr>

                      <!-- Row 3: Blank separator -->
                      <tr style="height: 6px;"><td colspan="10" style="border: none; background: #ffffff;"></td></tr>

                      <!-- Rows 4 & 5: App-style Final Calculation Summary Card (الحسبة النهائية زي التطبيق) -->
                      <tr>
                        ${if (isFixedEmployment) """
                        <td colspan="4" class="card-hdr">الساعات الأساسية</td>
                        <td colspan="3" class="card-hdr">ساعات إضافية (أوفر تايم)</td>
                        <td colspan="3" class="card-hdr">مستحق الإضافي (الفلوس)</td>
                        """.trimIndent() else """
                        <td colspan="4" class="card-hdr">الساعات (صافي بعد خصم البريك)</td>
                        <td colspan="3" class="card-hdr">أيام العمل الفعلية</td>
                        <td colspan="3" class="card-hdr">إجمالي الراتب (الفلوس)</td>
                        """.trimIndent()}
                      </tr>
                      <tr>
                        ${if (isFixedEmployment) """
                        <td colspan="4" class="card-val num" x:num="${"%.2f".format(totalRegularHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(H$firstDataRow:H$lastDataRow)\"" else ""}>${"%.2f".format(totalRegularHours)} س</td>
                        <td colspan="3" class="card-val num" style="color:#059669;" x:num="${"%.2f".format(totalOvertimeHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(I$firstDataRow:I$lastDataRow)\"" else ""}>+${"%.2f".format(totalOvertimeHours)} س</td>
                        <td colspan="3" class="card-val currency" x:num="${"%.2f".format(totalSalariedOvertimePay)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=ROUND(SUM(J$firstDataRow:J$lastDataRow), 2)\"" else ""}>${"%,.2f".format(totalSalariedOvertimePay)} ج.م</td>
                        """.trimIndent() else """
                        <td colspan="4" class="card-val num" x:num="${"%.2f".format(totalNetHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(I$firstDataRow:I$lastDataRow)\"" else ""}>${"%.2f".format(totalNetHours)} س</td>
                        <td colspan="3" class="card-val integer" x:num="$workedDays" ${if (sortedRecords.isNotEmpty()) "x:f=\"=COUNTIF(E$firstDataRow:E$lastDataRow, &quot;&gt;0&quot;)\"" else ""}>$workedDays يوم</td>
                        <td colspan="3" class="card-val currency" x:num="${"%.2f".format(totalHourlySalary)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=ROUND(SUM(J$firstDataRow:J$lastDataRow), 2)\"" else ""}>${"%,.2f".format(totalHourlySalary)} ج.م</td>
                        """.trimIndent()}
                      </tr>

                      <!-- Row 6: Blank separator -->
                      <tr style="height: 10px;"><td colspan="10" style="border: none; background: #ffffff;"></td></tr>

                      <!-- Row 7: Main Table Column Headers -->
                      <tr>
                        <th width="80">اليوم</th>
                        <th width="100">التاريخ</th>
                        <th width="85">الحضور</th>
                        <th width="85">الانصراف</th>
                        <th width="80">الساعات</th>
                        <th width="70">المصدر</th>
                        <th width="150">الملاحظات</th>
                        ${if (isFixedEmployment) """
                        <th width="80" class="th-calc">الأساسي</th>
                        <th width="90" class="th-calc">الإضافي</th>
                        <th width="115" class="th-calc">فلوس الإضافي</th>
                        """.trimIndent() else """
                        <th width="80" class="th-calc">البريك</th>
                        <th width="90" class="th-calc">الصافي</th>
                        <th width="115" class="th-calc">المرتب (ج.م)</th>
                        """.trimIndent()}
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
                val rawHours = if (isOff) 0.0 else record.hoursWorked
                val dayNotes = notesByDate[record.workDate]?.joinToString(" | ") { it.noteText } ?: record.note
                val source = when {
                    isOff -> "إجازة"
                    record.source == "ess_online" && !record.isFromServer -> "معدل"
                    record.source == "ess_online" -> "ESS"
                    else -> "يدوي"
                }

                val rowStyle = if (isOff) "background-color: #fef3c7;" else if (index % 2 == 1) "background-color: #f8fafc;" else ""

                if (isFixedEmployment) {
                    val baseHours = if (!isOff && rawHours > 0) minOf(standardShiftHours, rawHours) else 0.0
                    val otHours = if (!isOff && rawHours > standardShiftHours) (rawHours - standardShiftHours) else 0.0
                    val otWage = otHours * hourlyRate
                    append("""
                      <tr style="$rowStyle">
                        <td style="font-weight:bold;">$dayName</td>
                        <td>${record.workDate}</td>
                        <td style="color:${if (isOff) "#b45309" else "#059669"}; font-weight:bold;">$checkIn</td>
                        <td style="color:${if (isOff) "#b45309" else "#2563eb"}; font-weight:bold;">$checkOut</td>
                        <td class="num font-bold" x:num="${"%.2f".format(rawHours)}">${"%.2f".format(rawHours)}</td>
                        <td>$source</td>
                        <td style="text-align:right;">$dayNotes</td>
                        <td class="num" x:num="${"%.2f".format(baseHours)}" x:f="=IF(E$row&gt;0, MIN(${standardShiftHours.toInt()}, E$row), 0)">${"%.2f".format(baseHours)}</td>
                        <td class="num font-bold" style="color:${if (otHours > 0) "#059669" else "#64748b"};" x:num="${"%.2f".format(otHours)}" x:f="=IF(E$row&gt;$standardShiftHours, ROUND(E$row-$standardShiftHours, 2), 0)">${if (otHours > 0) "+%.2f".format(otHours) else "0.00"}</td>
                        <td class="currency" x:num="${"%.2f".format(otWage)}" x:f="=ROUND(I$row*$hourlyRate, 2)">${"%,.2f".format(otWage)}</td>
                      </tr>
                    """.trimIndent())
                } else {
                    val breakDeduction = if (!isOff && rawHours > 0.5) 0.5 else 0.0
                    val netHours = if (!isOff && rawHours > 0.5) (rawHours - 0.5) else 0.0
                    val dailyWage = netHours * hourlyRate
                    append("""
                      <tr style="$rowStyle">
                        <td style="font-weight:bold;">$dayName</td>
                        <td>${record.workDate}</td>
                        <td style="color:${if (isOff) "#b45309" else "#059669"}; font-weight:bold;">$checkIn</td>
                        <td style="color:${if (isOff) "#b45309" else "#2563eb"}; font-weight:bold;">$checkOut</td>
                        <td class="num font-bold" x:num="${"%.2f".format(rawHours)}">${"%.2f".format(rawHours)}</td>
                        <td>$source</td>
                        <td style="text-align:right;">$dayNotes</td>
                        <td class="num font-bold" style="color:#b45309;" x:num="${"%.2f".format(breakDeduction)}" x:f="=IF(E$row&gt;0.5, 0.5, 0)">${if (breakDeduction > 0) "-0.50" else "0.00"}</td>
                        <td class="num font-bold" style="color:#059669;" x:num="${"%.2f".format(netHours)}" x:f="=IF(E$row&gt;0.5, ROUND(E$row-0.5, 2), 0)">${"%.2f".format(netHours)}</td>
                        <td class="currency" x:num="${"%.2f".format(dailyWage)}" x:f="=ROUND(I$row*$hourlyRate, 2)">${"%,.2f".format(dailyWage)}</td>
                      </tr>
                    """.trimIndent())
                }
            }

            // Summary Section inside the table footer: Fully summed totals & money
            append("""
                    </tbody>
                    <tfoot>
                      <!-- Column Totals Row -->
                      <tr class="summary-row">
                        <td colspan="4" class="summary-lbl">الإجمالي العام:</td>
                        <td class="num font-bold" x:num="${"%.2f".format(totalRawHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(E$firstDataRow:E$lastDataRow)\"" else ""}>${"%.2f".format(totalRawHours)}</td>
                        <td colspan="2"></td>
                        ${if (isFixedEmployment) """
                        <td class="num font-bold" x:num="${"%.2f".format(totalRegularHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(H$firstDataRow:H$lastDataRow)\"" else ""}>${"%.2f".format(totalRegularHours)}</td>
                        <td class="num font-bold" style="color:#059669;" x:num="${"%.2f".format(totalOvertimeHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(I$firstDataRow:I$lastDataRow)\"" else ""}>+${"%.2f".format(totalOvertimeHours)}</td>
                        <td class="currency font-bold" style="color:#047857;" x:num="${"%.2f".format(totalSalariedOvertimePay)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=ROUND(SUM(J$firstDataRow:J$lastDataRow), 2)\"" else ""}>${"%,.2f".format(totalSalariedOvertimePay)}</td>
                        """.trimIndent() else """
                        <td class="num font-bold" style="color:#b45309;" x:num="${"%.2f".format(totalBreakHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(H$firstDataRow:H$lastDataRow)\"" else ""}>-${"%.2f".format(totalBreakHours)}</td>
                        <td class="num font-bold" style="color:#059669;" x:num="${"%.2f".format(totalNetHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(I$firstDataRow:I$lastDataRow)\"" else ""}>${"%.2f".format(totalNetHours)}</td>
                        <td class="currency font-bold" style="color:#047857;" x:num="${"%.2f".format(totalHourlySalary)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=ROUND(SUM(J$firstDataRow:J$lastDataRow), 2)\"" else ""}>${"%,.2f".format(totalHourlySalary)}</td>
                        """.trimIndent()}
                      </tr>

                      <!-- Attendance & Rate Stats Row -->
                      <tr class="summary-row">
                        <td colspan="4" class="summary-lbl">أيام العمل الفعلية:</td>
                        <td class="integer font-bold" x:num="$workedDays" ${if (sortedRecords.isNotEmpty()) "x:f=\"=COUNTIF(E$firstDataRow:E$lastDataRow, &quot;&gt;0&quot;)\"" else ""}>$workedDays يوم</td>
                        <td colspan="2" class="summary-lbl">أيام الإجازات (OFF):</td>
                        <td class="integer font-bold" style="color:#b45309;" x:num="$offDays" ${if (sortedRecords.isNotEmpty()) "x:f=\"=COUNTIF(E$firstDataRow:E$lastDataRow, &quot;=0&quot;)\"" else ""}>$offDays</td>
                        <td class="summary-lbl">سعر الساعة:</td>
                        <td class="currency font-bold" x:num="${"%.2f".format(hourlyRate)}">${"%,.0f".format(hourlyRate)} ج.م</td>
                      </tr>

                      <!-- Final Grand Total Bar (الحسبة النهائية المجمعة: التوتال والفلوس) -->
                      <tr class="total-bar">
                        <td colspan="7" class="total-lbl">
                          ${if (isFixedEmployment) "الحسبة النهائية (إجمالي مستحقات الأوفر تايم):" else "الحسبة النهائية (صافي الراتب المستحق بعد خصم البريك):"}
                        </td>
                        <td colspan="3" class="total-val currency" x:num="${"%.2f".format(if (isFixedEmployment) totalSalariedOvertimePay else totalHourlySalary)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=ROUND(SUM(J$firstDataRow:J$lastDataRow), 2)\"" else ""}>
                          ${"%,.2f".format(if (isFixedEmployment) totalSalariedOvertimePay else totalHourlySalary)} ج.م
                        </td>
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

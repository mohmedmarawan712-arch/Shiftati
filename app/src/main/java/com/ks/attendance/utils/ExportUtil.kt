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
     * File name: First two names of account owner + date.
     * Summation formulas placed after notes column:
     * - Salaried (تعيين): Basic hours, Overtime hours, Overtime pay.
     * - Hourly (ساعة): Break deduction (0.5h), Net hours, Total salary.
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

        // 1. Determine file name from first two names of account owner + date
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

        // 2. Metrics calculation
        val sortedRecords = records.sortedBy { it.checkInTime }
        val workedRecords = sortedRecords.filter { it.hoursWorked > 0.0 && it.source != "off_day" }
        val workedDays = workedRecords.groupBy { it.workDate }.keys.size
        val offDays = sortedRecords.filter { it.source == "off_day" }.map { it.workDate }.distinct().size

        // Total raw hours registered
        val totalRawHours = sortedRecords.sumOf { if (it.source == "off_day") 0.0 else it.hoursWorked }

        // Hourly calculations: 0.5 hour break per worked day (> 0.5 hours)
        val breakHoursPerRecord = sortedRecords.map { rec ->
            if (rec.source != "off_day" && rec.hoursWorked > 0.5) 0.5 else 0.0
        }
        val totalBreakHours = breakHoursPerRecord.sum()
        val totalNetHours = sortedRecords.mapIndexed { idx, rec ->
            val h = if (rec.source == "off_day") 0.0 else rec.hoursWorked
            maxOf(0.0, h - breakHoursPerRecord[idx])
        }.sum()
        val totalHourlySalary = totalNetHours * hourlyRate

        // Salaried calculations: Overtime exceeding standardShiftHours (e.g. 9.0)
        var totalOvertimeHours = 0.0
        sortedRecords.forEach { rec ->
            if (rec.source != "off_day" && rec.hoursWorked > standardShiftHours) {
                totalOvertimeHours += (rec.hoursWorked - standardShiftHours)
            }
        }
        val totalSalariedOvertimePay = totalOvertimeHours * hourlyRate
        val totalRegularHours = workedDays * standardShiftHours

        val firstDataRow = 2
        val lastDataRow = if (sortedRecords.isNotEmpty()) 1 + sortedRecords.size else 2
        val sheetName = if (isFixedEmployment) "تعيين - أوفر تايم" else "ساعة - ساعات وراتب"

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
                  table { border-collapse: collapse; width: 100%; margin-top: 10px; margin-bottom: 20px; }
                  th { background-color: #059669; color: #ffffff; padding: 10px 6px; border: 1px solid #047857; font-weight: bold; font-size: 13px; text-align: center; }
                  td { padding: 8px 6px; border: 1px solid #d1d5db; text-align: center; font-size: 12px; }
                  .th-calc { background-color: #047857; color: #fef08a; border: 1px solid #065f46; font-weight: bold; }
                  .header-box { background-color: #f8fafc; border: 1px solid #e2e8f0; padding: 14px; border-radius: 8px; margin-bottom: 12px; }
                  .title { font-size: 20px; font-weight: bold; color: #065f46; text-align: right; margin-bottom: 6px; }
                  .subtitle { font-size: 13px; color: #334155; text-align: right; line-height: 1.6; }
                  .summary-row { background-color: #f0fdf4; }
                  .lbl { text-align: right; font-weight: 600; color: #374151; padding-right: 12px; }
                  .total-row { background-color: #d1fae5; border-top: 2px solid #059669; font-weight: bold; }
                  .total-lbl { font-size: 14px; font-weight: bold; color: #047857; text-align: right; padding-right: 12px; }
                  .total-val { font-size: 15px; font-weight: bold; color: #047857; text-align: center; }
                  .font-bold { font-weight: bold; }
                  .num { mso-number-format:"0\.00"; text-align: center; }
                  .currency { mso-number-format:"\#\,\#\#0\.00"; text-align: center; font-weight: 600; }
                  .integer { mso-number-format:"0"; text-align: center; }
                  .summary-card { width: 100%; max-width: 600px; border-collapse: collapse; margin-top: 16px; border: 2px solid #059669; }
                  .card-head { background-color: #059669; color: #ffffff; font-size: 15px; font-weight: bold; padding: 10px; text-align: center; }
                  .card-lbl { background-color: #f8fafc; font-weight: 600; text-align: right; padding: 8px 12px; border: 1px solid #e2e8f0; width: 60%; }
                  .card-val { background-color: #ffffff; font-weight: bold; text-align: center; padding: 8px 12px; border: 1px solid #e2e8f0; width: 40%; }
                  .card-total { background-color: #ecfdf5; color: #047857; font-size: 15px; font-weight: bold; padding: 10px 12px; border: 1px solid #a7f3d0; }
                </style>
                </head>
                <body dir="rtl">
                  <div class="header-box">
                    <div class="title">تقرير ساعات العمل والرواتب (شفتاتي)</div>
                    <div class="subtitle">
                      <b>اسم صاحب الحساب:</b> ${rawName} &nbsp;|&nbsp;
                      <b>الفترة المحتسبة:</b> من $periodStart إلى $periodEnd &nbsp;|&nbsp;
                      <b>تاريخ التصدير:</b> ${sdfDateTime.format(Date())}<br/>
                      <b>نظام الاحتساب:</b> ${if (isFixedEmployment) "نظام التعيين (ساعات العمل الأساسية $standardShiftHours س، واحتساب إضافي أوفر تايم)" else "نظام الساعة (مع خصم 0.5 ساعة بريك تلقائياً لكل يوم عمل)"}
                    </div>
                  </div>
                  <table>
                    <thead>
                      <tr>
                        <th>اليوم</th>
                        <th>التاريخ</th>
                        <th>الحضور</th>
                        <th>الانصراف</th>
                        <th>الساعات الفعلية</th>
                        <th>المصدر</th>
                        <th>الملاحظات</th>
                        ${if (isFixedEmployment) """
                        <th class="th-calc">الساعات الأساسية (س)</th>
                        <th class="th-calc">ساعات الأوفر تايم (س)</th>
                        <th class="th-calc">فلوس الأوفر تايم (ج.م)</th>
                        """.trimIndent() else """
                        <th class="th-calc">خصم البريك (س)</th>
                        <th class="th-calc">صافي الساعات (س)</th>
                        <th class="th-calc">المرتب المستحق (ج.م)</th>
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

                val rowStyle = if (isOff) "background-color: #fffbeb;" else ""

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
                        <td class="num font-bold" style="color:${if (otHours > 0) "#059669" else "#6b7280"};" x:num="${"%.2f".format(otHours)}" x:f="=IF(E$row&gt;$standardShiftHours, ROUND(E$row-$standardShiftHours, 2), 0)">${if (otHours > 0) "+%.2f".format(otHours) else "0.00"}</td>
                        <td class="currency" x:num="${"%.2f".format(otWage)}" x:f="=ROUND(I$row*$hourlyRate, 2)">${"%,.2f".format(otWage)}</td>
                      </tr>
                    """.trimIndent())
                } else {
                    // Hourly: 0.5h break deducted from any worked shift > 0.5h
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

            // Summary row directly underneath columns
            append("""
                    </tbody>
                    <tfoot>
                      <tr class="summary-row">
                        <td colspan="4" class="lbl">إجمالي الساعات المسجلة:</td>
                        <td class="num font-bold" x:num="${"%.2f".format(totalRawHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(E$firstDataRow:E$lastDataRow)\"" else ""}>${"%.2f".format(totalRawHours)} س</td>
                        <td colspan="2"></td>
                        ${if (isFixedEmployment) """
                        <td class="num font-bold" x:num="${"%.2f".format(totalRegularHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(H$firstDataRow:H$lastDataRow)\"" else ""}>${"%.2f".format(totalRegularHours)} س</td>
                        <td class="num font-bold" style="color:#059669;" x:num="${"%.2f".format(totalOvertimeHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(I$firstDataRow:I$lastDataRow)\"" else ""}>+${"%.2f".format(totalOvertimeHours)} س</td>
                        <td class="currency font-bold" style="color:#047857;" x:num="${"%.2f".format(totalSalariedOvertimePay)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=ROUND(SUM(J$firstDataRow:J$lastDataRow), 2)\"" else ""}>${"%,.2f".format(totalSalariedOvertimePay)} ج.م</td>
                        """.trimIndent() else """
                        <td class="num font-bold" style="color:#b45309;" x:num="${"%.2f".format(totalBreakHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(H$firstDataRow:H$lastDataRow)\"" else ""}>-${"%.2f".format(totalBreakHours)} س</td>
                        <td class="num font-bold" style="color:#059669;" x:num="${"%.2f".format(totalNetHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(I$firstDataRow:I$lastDataRow)\"" else ""}>${"%.2f".format(totalNetHours)} س</td>
                        <td class="currency font-bold" style="color:#047857;" x:num="${"%.2f".format(totalHourlySalary)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=ROUND(SUM(J$firstDataRow:J$lastDataRow), 2)\"" else ""}>${"%,.2f".format(totalHourlySalary)} ج.م</td>
                        """.trimIndent()}
                      </tr>
                    </tfoot>
                  </table>
            """.trimIndent())

            // Standalone Summary & Formula Card after the table
            if (isFixedEmployment) {
                append("""
                  <table class="summary-card">
                    <thead>
                      <tr>
                        <th colspan="2" class="card-head">ملخص نظام التعيين ودوال الجمع (الأوفر تايم)</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td class="card-lbl">إجمالي الساعات الفعلية المسجلة:</td>
                        <td class="card-val num" x:num="${"%.2f".format(totalRawHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(E$firstDataRow:E$lastDataRow)\"" else ""}>${"%.2f".format(totalRawHours)} ساعة</td>
                      </tr>
                      <tr>
                        <td class="card-lbl">إجمالي الساعات الأساسية ($workedDays يوم × ${standardShiftHours.toInt()} س):</td>
                        <td class="card-val num" style="color:#2563eb;" x:num="${"%.2f".format(totalRegularHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(H$firstDataRow:H$lastDataRow)\"" else ""}>${"%.2f".format(totalRegularHours)} ساعة</td>
                      </tr>
                      <tr>
                        <td class="card-lbl">إجمالي ساعات الأوفر تايم (الإضافي):</td>
                        <td class="card-val num" style="color:#059669;" x:num="${"%.2f".format(totalOvertimeHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(I$firstDataRow:I$lastDataRow)\"" else ""}>+${"%.2f".format(totalOvertimeHours)} ساعة</td>
                      </tr>
                      <tr>
                        <td class="card-lbl">سعر ساعة الأوفر تايم:</td>
                        <td class="card-val currency" x:num="${"%.2f".format(hourlyRate)}">${"%,.0f".format(hourlyRate)} ج.م</td>
                      </tr>
                      <tr>
                        <td class="card-lbl">عدد أيام العمل الفعلية:</td>
                        <td class="card-val integer" x:num="$workedDays" ${if (sortedRecords.isNotEmpty()) "x:f=\"=COUNTIF(E$firstDataRow:E$lastDataRow, &quot;&gt;0&quot;)\"" else ""}>$workedDays يوم</td>
                      </tr>
                      <tr>
                        <td class="card-lbl">عدد أيام الإجازات (OFF):</td>
                        <td class="card-val integer" style="color:#b45309;" x:num="$offDays" ${if (sortedRecords.isNotEmpty()) "x:f=\"=COUNTIF(E$firstDataRow:E$lastDataRow, &quot;=0&quot;)\"" else ""}>$offDays يوم</td>
                      </tr>
                      <tr>
                        <td class="card-lbl card-total">إجمالي فلوس ومستحقات الأوفر تايم:</td>
                        <td class="card-val card-total currency" x:num="${"%.2f".format(totalSalariedOvertimePay)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=ROUND(SUM(J$firstDataRow:J$lastDataRow), 2)\"" else ""}>${"%,.2f".format(totalSalariedOvertimePay)} ج.م</td>
                      </tr>
                    </tbody>
                  </table>
                """.trimIndent())
            } else {
                append("""
                  <table class="summary-card">
                    <thead>
                      <tr>
                        <th colspan="2" class="card-head">ملخص نظام الساعة ودوال الجمع (مع خصم استراحة البريك)</th>
                      </tr>
                    </thead>
                    <tbody>
                      <tr>
                        <td class="card-lbl">إجمالي الساعات المسجلة الكاملة:</td>
                        <td class="card-val num" x:num="${"%.2f".format(totalRawHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(E$firstDataRow:E$lastDataRow)\"" else ""}>${"%.2f".format(totalRawHours)} ساعة</td>
                      </tr>
                      <tr>
                        <td class="card-lbl">إجمالي استراحة البريك المخصومة (0.5 س/يوم):</td>
                        <td class="card-val num" style="color:#b45309;" x:num="${"%.2f".format(totalBreakHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(H$firstDataRow:H$lastDataRow)\"" else ""}>-${"%.2f".format(totalBreakHours)} ساعة</td>
                      </tr>
                      <tr>
                        <td class="card-lbl">صافي ساعات العمل المحتسبة:</td>
                        <td class="card-val num" style="color:#059669;" x:num="${"%.2f".format(totalNetHours)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=SUM(I$firstDataRow:I$lastDataRow)\"" else ""}>${"%.2f".format(totalNetHours)} ساعة</td>
                      </tr>
                      <tr>
                        <td class="card-lbl">سعر الساعة التعاقدي:</td>
                        <td class="card-val currency" x:num="${"%.2f".format(hourlyRate)}">${"%,.0f".format(hourlyRate)} ج.م</td>
                      </tr>
                      <tr>
                        <td class="card-lbl">عدد أيام العمل الفعلية:</td>
                        <td class="card-val integer" x:num="$workedDays" ${if (sortedRecords.isNotEmpty()) "x:f=\"=COUNTIF(E$firstDataRow:E$lastDataRow, &quot;&gt;0&quot;)\"" else ""}>$workedDays يوم</td>
                      </tr>
                      <tr>
                        <td class="card-lbl">عدد أيام الإجازات (OFF):</td>
                        <td class="card-val integer" style="color:#b45309;" x:num="$offDays" ${if (sortedRecords.isNotEmpty()) "x:f=\"=COUNTIF(E$firstDataRow:E$lastDataRow, &quot;=0&quot;)\"" else ""}>$offDays يوم</td>
                      </tr>
                      <tr>
                        <td class="card-lbl card-total">إجمالي صافي المرتب المستحق:</td>
                        <td class="card-val card-total currency" x:num="${"%.2f".format(totalHourlySalary)}" ${if (sortedRecords.isNotEmpty()) "x:f=\"=ROUND(SUM(J$firstDataRow:J$lastDataRow), 2)\"" else ""}>${"%,.2f".format(totalHourlySalary)} ج.م</td>
                      </tr>
                    </tbody>
                  </table>
                  <div style="font-size: 11px; color: #64748b; margin-top: 6px;">* تم خصم نصف ساعة (0.5 ساعة) تلقائياً كاستراحة بريك من كل يوم عمل به ساعات عمل مسجلة.</div>
                """.trimIndent())
            }

            append("""
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

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
    private val sdfTime = SimpleDateFormat("HH:mm", Locale.US)

    private fun getSafeFileName(employeeName: String): String {
        val rawName = employeeName.trim().ifBlank { "شفتاتي" }
        val nameWords = rawName.split(Regex("\\s+")).filter { it.isNotBlank() }
        val firstTwoNames = when {
            nameWords.size >= 2 -> "${nameWords[0]}_${nameWords[1]}"
            nameWords.isNotEmpty() -> nameWords[0]
            else -> "شفتاتي"
        }
        return firstTwoNames.replace(Regex("[/\\\\:*?\"<>|]"), "")
    }

    /**
     * Exports attendance records to a universal, standard CSV spreadsheet (.csv) with UTF-8 BOM.
     * Fully compatible with all spreadsheet apps (Excel, Google Sheets, LibreOffice, phone viewers).
     */
    fun exportCsv(
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
        val dir = getExportDir(context)
        val safeName = getSafeFileName(employeeName)
        val datePart = if (periodStart.isNotBlank() && periodEnd.isNotBlank()) "${periodStart}_${periodEnd}" else sdfDate.format(Date())
        val file = File(dir, "${safeName}_${datePart}.csv")

        val sortedRecords = records.sortedWith(compareBy<AttendanceRecord> { it.workDate }.thenBy { it.checkInTime })
        val workedRecords = sortedRecords.filter { it.hoursWorked > 0.0 && it.source != "off_day" }
        val workedDays = workedRecords.groupBy { it.workDate }.keys.size
        val offDays = sortedRecords.filter { it.source == "off_day" }.map { it.workDate }.distinct().size
        val totalRawHours = sortedRecords.sumOf { if (it.source == "off_day") 0.0 else it.hoursWorked }

        val breakHoursPerRecord = sortedRecords.map { rec ->
            if (rec.source != "off_day" && rec.hoursWorked > 0.5) 0.5 else 0.0
        }
        val totalBreakHours = breakHoursPerRecord.sum()
        val totalNetHours = sortedRecords.mapIndexed { idx, rec ->
            val h = if (rec.source == "off_day") 0.0 else rec.hoursWorked
            maxOf(0.0, h - breakHoursPerRecord[idx])
        }.sum()
        val totalHourlySalary = totalNetHours * hourlyRate

        var totalOvertimeHours = 0.0
        sortedRecords.forEach { rec ->
            if (rec.source != "off_day" && rec.hoursWorked > standardShiftHours) {
                totalOvertimeHours += (rec.hoursWorked - standardShiftHours)
            }
        }
        val totalSalariedOvertimePay = totalOvertimeHours * hourlyRate

        val notesByDate = notes.groupBy { it.date }

        val csv = buildString {
            // Header summary
            appendLine("\"تقرير حضور وانصراف - شفتاتي\"")
            appendLine("\"الموظف\",\"${employeeName.ifBlank { "شفتاتي" }}\"")
            appendLine("\"الفترة\",\"من $periodStart إلى $periodEnd\"")
            appendLine("\"نظام العمل\",\"${if (isFixedEmployment) "نظام التعيين" else "نظام الساعة"}\"")
            if (isFixedEmployment) {
                appendLine("\"أيام العمل\",\"$workedDays\",\"أيام الإجازة\",\"$offDays\"")
                appendLine("\"الساعات الأساسية\",\"${workedDays * standardShiftHours} س\",\"ساعات الإضافي\",\"${PayrollUtil.formatHours(totalOvertimeHours)}\"")
                appendLine("\"أجر الإضافي\",\"${PayrollUtil.formatCurrency(totalSalariedOvertimePay)}\"")
            } else {
                appendLine("\"أيام العمل\",\"$workedDays\",\"أيام الإجازة\",\"$offDays\"")
                appendLine("\"إجمالي الساعات\",\"${PayrollUtil.formatHours(totalRawHours)}\",\"خصم البريك\",\"-${PayrollUtil.formatHours(totalBreakHours)}\"")
                appendLine("\"صافي الساعات\",\"${PayrollUtil.formatHours(totalNetHours)}\",\"إجمالي الراتب\",\"${PayrollUtil.formatCurrency(totalHourlySalary)}\"")
            }
            appendLine()

            // Columns
            appendLine("\"التاريخ\",\"اليوم\",\"وقت الحضور\",\"وقت الانصراف\",\"الساعات\",\"الخصم\",\"الصافي\",\"الحالة\",\"الملاحظات\"")

            sortedRecords.forEachIndexed { idx, rec ->
                val cal = Calendar.getInstance().apply { timeInMillis = rec.checkInTime }
                val dayName = arabicDayName(cal.time)
                val isOff = (rec.source == "off_day")
                val inTimeStr = if (isOff) "-" else sdfTime.format(Date(rec.checkInTime))
                val outTimeStr = if (isOff) "-" else rec.checkOutTime?.let { sdfTime.format(Date(it)) } ?: "مفتوح"
                val rawH = if (isOff) 0.0 else rec.hoursWorked
                val breakH = breakHoursPerRecord[idx]
                val netH = maxOf(0.0, rawH - breakH)
                val status = when {
                    isOff -> "إجازة (OFF)"
                    rec.checkOutTime == null -> "انصراف مفقود"
                    rec.isFromServer -> "سيرفر ESS"
                    else -> "يدوي / معدل"
                }
                val dayNotes = notesByDate[rec.workDate]?.map { it.noteText }?.filter { it.isNotBlank() } ?: emptyList()
                val allNotes = (listOf(rec.note.trim()) + dayNotes).filter { it.isNotBlank() }.distinct().joinToString(" | ")

                val hoursStr = if (isOff) "-" else PayrollUtil.formatHours(rawH)
                val breakStr = if (isOff || breakH == 0.0) "-" else "-${PayrollUtil.formatHours(breakH)}"
                val netStr = if (isOff) "-" else PayrollUtil.formatHours(netH)

                appendLine("\"${rec.workDate}\",\"$dayName\",\"$inTimeStr\",\"$outTimeStr\",\"$hoursStr\",\"$breakStr\",\"$netStr\",\"$status\",\"${allNotes.replace("\"", "\"\"")}\"")
            }
        }

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                writer.write("\uFEFF") // UTF-8 BOM
                writer.write(csv)
                writer.flush()
            }
        }

        file.absolutePath
    }

    /**
     * Exports attendance records to a clean, mobile-optimized HTML document (.html).
     * Opens directly in any phone's browser, formatted with RTL layout, summary cards, and printable styling.
     */
    fun exportHtmlReport(
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
        val dir = getExportDir(context)
        val safeName = getSafeFileName(employeeName)
        val datePart = if (periodStart.isNotBlank() && periodEnd.isNotBlank()) "${periodStart}_${periodEnd}" else sdfDate.format(Date())
        val file = File(dir, "${safeName}_${datePart}.html")

        val sortedRecords = records.sortedWith(compareBy<AttendanceRecord> { it.workDate }.thenBy { it.checkInTime })
        val workedRecords = sortedRecords.filter { it.hoursWorked > 0.0 && it.source != "off_day" }
        val workedDays = workedRecords.groupBy { it.workDate }.keys.size
        val offDays = sortedRecords.filter { it.source == "off_day" }.map { it.workDate }.distinct().size
        val totalRawHours = sortedRecords.sumOf { if (it.source == "off_day") 0.0 else it.hoursWorked }

        val breakHoursPerRecord = sortedRecords.map { rec ->
            if (rec.source != "off_day" && rec.hoursWorked > 0.5) 0.5 else 0.0
        }
        val totalBreakHours = breakHoursPerRecord.sum()
        val totalNetHours = sortedRecords.mapIndexed { idx, rec ->
            val h = if (rec.source == "off_day") 0.0 else rec.hoursWorked
            maxOf(0.0, h - breakHoursPerRecord[idx])
        }.sum()
        val totalHourlySalary = totalNetHours * hourlyRate

        var totalOvertimeHours = 0.0
        sortedRecords.forEach { rec ->
            if (rec.source != "off_day" && rec.hoursWorked > standardShiftHours) {
                totalOvertimeHours += (rec.hoursWorked - standardShiftHours)
            }
        }
        val totalSalariedOvertimePay = totalOvertimeHours * hourlyRate

        val notesByDate = notes.groupBy { it.date }

        val html = buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html dir=\"rtl\" lang=\"ar\">")
            appendLine("<head>")
            appendLine("<meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">")
            appendLine("<title>تقرير حضور وانصراف - $employeeName</title>")
            appendLine("<style>")
            appendLine("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background: #f8fafc; color: #1e293b; margin: 0; padding: 16px; direction: rtl; }")
            appendLine(".card { background: #ffffff; border-radius: 16px; padding: 20px; box-shadow: 0 4px 6px -1px rgba(0,0,0,0.05); margin-bottom: 16px; border: 1px solid #e2e8f0; }")
            appendLine(".header-title { font-size: 20px; font-weight: bold; color: #0f172a; margin-bottom: 4px; }")
            appendLine(".header-sub { font-size: 13px; color: #64748b; }")
            appendLine(".grid-summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 12px; margin-top: 14px; }")
            appendLine(".summary-box { background: #f1f5f9; border-radius: 12px; padding: 12px; text-align: center; }")
            appendLine(".summary-label { font-size: 11px; color: #64748b; margin-bottom: 4px; }")
            appendLine(".summary-val { font-size: 16px; font-weight: bold; color: #0f172a; }")
            appendLine(".summary-val.highlight { color: #0284c7; }")
            appendLine(".summary-val.green { color: #16a34a; }")
            appendLine("table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 13px; }")
            appendLine("th { background: #f8fafc; color: #475569; padding: 10px 8px; text-align: right; border-bottom: 2px solid #e2e8f0; font-weight: 600; }")
            appendLine("td { padding: 10px 8px; border-bottom: 1px solid #f1f5f9; }")
            appendLine("tr:nth-child(even) td { background: #fafbfc; }")
            appendLine(".badge { display: inline-block; padding: 3px 8px; border-radius: 6px; font-size: 11px; font-weight: 600; }")
            appendLine(".badge-ess { background: #e0f2fe; color: #0369a1; }")
            appendLine(".badge-manual { background: #fef3c7; color: #b45309; }")
            appendLine(".badge-off { background: #f1f5f9; color: #64748b; }")
            appendLine(".badge-missing { background: #fee2e2; color: #b91c1c; }")
            appendLine("@media print { body { background: #ffffff; padding: 0; } .card { box-shadow: none; border: 1px solid #ccc; } }")
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")

            // Top Header Card
            appendLine("<div class=\"card\">")
            appendLine("<div class=\"header-title\">مسير الحضور والانصراف - شفتاتي</div>")
            appendLine("<div class=\"header-sub\">الموظف: <b>${employeeName.ifBlank { "شفتاتي" }}</b> • الفترة: <b>من $periodStart إلى $periodEnd</b></div>")

            // Summary Grid
            appendLine("<div class=\"grid-summary\">")
            appendLine("<div class=\"summary-box\"><div class=\"summary-label\">نظام العمل</div><div class=\"summary-val\">${if (isFixedEmployment) "نظام التعيين" else "نظام الساعة"}</div></div>")
            appendLine("<div class=\"summary-box\"><div class=\"summary-label\">أيام العمل</div><div class=\"summary-val highlight\">$workedDays يوم</div></div>")
            appendLine("<div class=\"summary-box\"><div class=\"summary-label\">أيام الإجازة</div><div class=\"summary-val\">$offDays يوم</div></div>")
            if (isFixedEmployment) {
                appendLine("<div class=\"summary-box\"><div class=\"summary-label\">ساعات الإضافي</div><div class=\"summary-val highlight\">${PayrollUtil.formatHours(totalOvertimeHours)}</div></div>")
                appendLine("<div class=\"summary-box\"><div class=\"summary-label\">مستحق الإضافي</div><div class=\"summary-val green\">${PayrollUtil.formatCurrency(totalSalariedOvertimePay)}</div></div>")
            } else {
                appendLine("<div class=\"summary-box\"><div class=\"summary-label\">صافي الساعات</div><div class=\"summary-val highlight\">${PayrollUtil.formatHours(totalNetHours)}</div></div>")
                appendLine("<div class=\"summary-box\"><div class=\"summary-label\">إجمالي الراتب</div><div class=\"summary-val green\">${PayrollUtil.formatCurrency(totalHourlySalary)}</div></div>")
            }
            appendLine("</div>")
            appendLine("</div>")

            // Records Table Card
            appendLine("<div class=\"card\">")
            appendLine("<div class=\"header-title\" style=\"font-size: 16px; margin-bottom: 12px;\">سجل الورديات اليومية (${sortedRecords.size})</div>")
            appendLine("<div style=\"overflow-x: auto;\">")
            appendLine("<table>")
            appendLine("<thead>")
            appendLine("<tr>")
            appendLine("<th>التاريخ</th>")
            appendLine("<th>اليوم</th>")
            appendLine("<th>الحضور</th>")
            appendLine("<th>الانصراف</th>")
            appendLine("<th>الساعات</th>")
            appendLine("<th>الخصم</th>")
            appendLine("<th>الصافي</th>")
            appendLine("<th>المصدر</th>")
            appendLine("<th>الملاحظات</th>")
            appendLine("</tr>")
            appendLine("</thead>")
            appendLine("<tbody>")

            sortedRecords.forEachIndexed { idx, rec ->
                val cal = Calendar.getInstance().apply { timeInMillis = rec.checkInTime }
                val dayName = arabicDayName(cal.time)
                val isOff = (rec.source == "off_day")
                val inTimeStr = if (isOff) "-" else sdfTime.format(Date(rec.checkInTime))
                val outTimeStr = if (isOff) "-" else rec.checkOutTime?.let { sdfTime.format(Date(it)) } ?: "مفتوح"
                val rawH = if (isOff) 0.0 else rec.hoursWorked
                val breakH = breakHoursPerRecord[idx]
                val netH = maxOf(0.0, rawH - breakH)

                val (badgeClass, statusText) = when {
                    isOff -> "badge-off" to "إجازة (OFF)"
                    rec.checkOutTime == null -> "badge-missing" to "انصراف مفقود"
                    rec.isFromServer -> "badge-ess" to "سيرفر ESS"
                    else -> "badge-manual" to "يدوي / معدل"
                }

                val dayNotes = notesByDate[rec.workDate]?.map { it.noteText }?.filter { it.isNotBlank() } ?: emptyList()
                val allNotes = (listOf(rec.note.trim()) + dayNotes).filter { it.isNotBlank() }.distinct().joinToString(" | ")

                appendLine("<tr>")
                appendLine("<td><b>${rec.workDate}</b></td>")
                appendLine("<td>$dayName</td>")
                appendLine("<td>$inTimeStr</td>")
                appendLine("<td>$outTimeStr</td>")
                appendLine("<td>${if (isOff) "-" else PayrollUtil.formatHours(rawH)}</td>")
                appendLine("<td>${if (isOff || breakH == 0.0) "-" else "-${PayrollUtil.formatHours(breakH)}"}</td>")
                appendLine("<td><b>${if (isOff) "-" else PayrollUtil.formatHours(netH)}</b></td>")
                appendLine("<td><span class=\"badge $badgeClass\">$statusText</span></td>")
                appendLine("<td style=\"color: #64748b;\">${allNotes.ifBlank { "-" }}</td>")
                appendLine("</tr>")
            }

            appendLine("</tbody>")
            appendLine("</table>")
            appendLine("</div>")
            appendLine("</div>")

            appendLine("</body>")
            appendLine("</html>")
        }

        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                writer.write(html)
                writer.flush()
            }
        }

        file.absolutePath
    }

    /** Legacy helper forwarding to exportCsv for backward compatibility */
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
    ): Result<String> = exportCsv(
        context, records, notes, periodStart, periodEnd, hourlyRate, isFixedEmployment, standardShiftHours, employeeName
    )

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

            val mimeType = when {
                filePath.endsWith(".csv", ignoreCase = true) -> "text/csv"
                filePath.endsWith(".html", ignoreCase = true) -> "text/html"
                filePath.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "تقرير حضور وانصراف - شفتاتي")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "مشاركة أو فتح التقرير").apply {
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

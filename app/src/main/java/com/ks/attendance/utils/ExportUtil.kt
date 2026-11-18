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
        val sanitized = firstTwoNames.replace(Regex("[^a-zA-Z0-9_\\-\\u0600-\\u06FF]"), "")
        return sanitized.ifBlank { "شفتاتي" }
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
                appendLine("\"صافي الساعات\",\"${PayrollUtil.formatHours(totalNetHours)}\",\"إجمالي المرتب\",\"${PayrollUtil.formatCurrency(totalHourlySalary)}\"")
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
            appendLine(":root {")
            appendLine("  --bg-page: #f8fafc;")
            appendLine("  --text-primary: #0f172a;")
            appendLine("  --text-secondary: #64748b;")
            appendLine("  --card-bg: #ffffff;")
            appendLine("  --card-border: #e2e8f0;")
            appendLine("  --card-shadow: 0 4px 6px -1px rgba(0,0,0,0.05);")
            appendLine("  --box-bg: #f1f5f9;")
            appendLine("  --table-header-bg: #f8fafc;")
            appendLine("  --table-header-text: #475569;")
            appendLine("  --table-row-even: #fafbfc;")
            appendLine("  --table-border: #f1f5f9;")
            appendLine("  --badge-ess-bg: #e0f2fe;")
            appendLine("  --badge-ess-text: #0369a1;")
            appendLine("  --badge-manual-bg: #fef3c7;")
            appendLine("  --badge-manual-text: #b45309;")
            appendLine("  --badge-off-bg: #f1f5f9;")
            appendLine("  --badge-off-text: #64748b;")
            appendLine("  --badge-missing-bg: #fee2e2;")
            appendLine("  --badge-missing-text: #b91c1c;")
            appendLine("  --accent: #0284c7;")
            appendLine("  --green: #16a34a;")
            appendLine("}")
            appendLine("@media (prefers-color-scheme: dark) {")
            appendLine("  :root {")
            appendLine("    --bg-page: #0b0f17;")
            appendLine("    --text-primary: #f1f5f9;")
            appendLine("    --text-secondary: #94a3b8;")
            appendLine("    --card-bg: #141c28;")
            appendLine("    --card-border: #233145;")
            appendLine("    --card-shadow: 0 4px 12px rgba(0,0,0,0.4);")
            appendLine("    --box-bg: #1b2536;")
            appendLine("    --table-header-bg: #182232;")
            appendLine("    --table-header-text: #94a3b8;")
            appendLine("    --table-row-even: #101722;")
            appendLine("    --table-border: #1e293b;")
            appendLine("    --badge-ess-bg: #0c4a6e;")
            appendLine("    --badge-ess-text: #38bdf8;")
            appendLine("    --badge-manual-bg: #451a03;")
            appendLine("    --badge-manual-text: #fbbf24;")
            appendLine("    --badge-off-bg: #1e293b;")
            appendLine("    --badge-off-text: #94a3b8;")
            appendLine("    --badge-missing-bg: #450a0a;")
            appendLine("    --badge-missing-text: #f87171;")
            appendLine("    --accent: #38bdf8;")
            appendLine("    --green: #4ade80;")
            appendLine("  }")
            appendLine("}")
            appendLine("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Cairo', 'Noto Sans Arabic', Arial, sans-serif; background: var(--bg-page); color: var(--text-primary); margin: 0; padding: 16px; direction: rtl; transition: background 0.3s, color 0.3s; }")
            appendLine(".card { background: var(--card-bg); border-radius: 16px; padding: 20px; box-shadow: var(--card-shadow); margin-bottom: 16px; border: 1px solid var(--card-border); }")
            appendLine(".header-title { font-size: 20px; font-weight: bold; color: var(--text-primary); margin-bottom: 4px; }")
            appendLine(".header-sub { font-size: 13px; color: var(--text-secondary); }")
            appendLine(".grid-summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 12px; margin-top: 14px; }")
            appendLine(".summary-box { background: var(--box-bg); border-radius: 12px; padding: 12px; text-align: center; border: 1px solid var(--card-border); }")
            appendLine(".summary-label { font-size: 11px; color: var(--text-secondary); margin-bottom: 4px; }")
            appendLine(".summary-val { font-size: 16px; font-weight: bold; color: var(--text-primary); }")
            appendLine(".summary-val.highlight { color: var(--accent); }")
            appendLine(".summary-val.green { color: var(--green); }")
            appendLine("table { width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 13px; }")
            appendLine("th { background: var(--table-header-bg); color: var(--table-header-text); padding: 10px 8px; text-align: right; border-bottom: 2px solid var(--card-border); font-weight: 600; }")
            appendLine("td { padding: 10px 8px; border-bottom: 1px solid var(--table-border); color: var(--text-primary); }")
            appendLine("tr:nth-child(even) td { background: var(--table-row-even); }")
            appendLine(".badge { display: inline-block; padding: 3px 8px; border-radius: 6px; font-size: 11px; font-weight: 600; }")
            appendLine(".badge-ess { background: var(--badge-ess-bg); color: var(--badge-ess-text); }")
            appendLine(".badge-manual { background: var(--badge-manual-bg); color: var(--badge-manual-text); }")
            appendLine(".badge-off { background: var(--badge-off-bg); color: var(--badge-off-text); }")
            appendLine(".badge-missing { background: var(--badge-missing-bg); color: var(--badge-missing-text); }")
            appendLine("@media print {")
            appendLine("  .no-print { display: none !important; }")
            appendLine("  body { background: #ffffff !important; color: #000000 !important; padding: 0 !important; }")
            appendLine("  .card { background: #ffffff !important; box-shadow: none !important; border: 1px solid #ccc !important; }")
            appendLine("  .summary-box { background: #f9f9f9 !important; border: 1px solid #ddd !important; }")
            appendLine("  th { background: #f0f0f0 !important; color: #000 !important; border-bottom: 2px solid #333 !important; }")
            appendLine("  td { border-bottom: 1px solid #ddd !important; color: #000 !important; }")
            appendLine("  tr:nth-child(even) td { background: #fafafa !important; }")
            appendLine("}")
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")

            // Print / Save as PDF Button
            appendLine("<div class=\"no-print\" style=\"display: flex; justify-content: flex-end; margin-bottom: 12px;\">")
            appendLine("<button onclick=\"window.print()\" style=\"background: var(--accent); color: #ffffff; border: none; padding: 10px 18px; border-radius: 10px; font-weight: bold; cursor: pointer; font-size: 13px; font-family: inherit; box-shadow: 0 2px 4px rgba(0,0,0,0.2);\">طباعة / حفظ PDF</button>")
            appendLine("</div>")

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
                appendLine("<div class=\"summary-box\"><div class=\"summary-label\">إجمالي المرتب</div><div class=\"summary-val green\">${PayrollUtil.formatCurrency(totalHourlySalary)}</div></div>")
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
        val candidates = listOfNotNull(
            context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.let { File(it, "Shiftati") },
            context.getExternalFilesDir(null)?.let { File(it, "Shiftati") },
            File(context.filesDir, "exports")
        )
        for (dir in candidates) {
            try {
                if (!dir.exists()) dir.mkdirs()
                val testFile = File(dir, ".test_write_${System.currentTimeMillis()}")
                testFile.writeText("ok")
                testFile.delete()
                return dir
            } catch (_: Exception) {
                continue
            }
        }
        val fallback = File(context.filesDir, "exports")
        if (!fallback.exists()) fallback.mkdirs()
        return fallback
    }

    /**
     * Open exported file in browser or default viewer app using ACTION_VIEW intent
     */
    fun openExportedFile(context: Context, filePath: String) {
        try {
            val file = File(filePath)
            if (!file.exists()) return

            val uri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val mimeType = when {
                filePath.endsWith(".html", ignoreCase = true) -> "text/html"
                filePath.endsWith(".csv", ignoreCase = true) -> "text/csv"
                filePath.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                else -> "*/*"
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val chooser = Intent.createChooser(intent, "فتح التقرير").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Exception) {
            // Silently ignore if no app is available
        }
    }

    /**
     * Share exported file using Android system intent with FileProvider
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

            val chooser = Intent.createChooser(intent, "مشاركة التقرير").apply {
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

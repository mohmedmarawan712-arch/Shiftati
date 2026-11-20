package com.ks.attendance.utils

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
        val safeName = getSafeFileName(employeeName)
        val datePart = if (periodStart.isNotBlank() && periodEnd.isNotBlank()) "${periodStart}_${periodEnd}" else sdfDate.format(Date())
        val fileName = "${safeName}_${datePart}.csv"

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

        saveToPublicDownloads(context, fileName, "\uFEFF" + csv, "text/csv").getOrThrow()
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
        val safeName = getSafeFileName(employeeName)
        val datePart = if (periodStart.isNotBlank() && periodEnd.isNotBlank()) "${periodStart}_${periodEnd}" else sdfDate.format(Date())
        val fileName = "${safeName}_${datePart}.html"

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
            val pageTitle = employeeName.ifBlank { "تقرير الحضور والانصراف" }
            appendLine("<!DOCTYPE html>")
            appendLine("<html dir=\"rtl\" lang=\"ar\">")
            appendLine("<head>")
            appendLine("<meta charset=\"utf-8\">")
            appendLine("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, viewport-fit=cover\">")
            appendLine("<meta name=\"color-scheme\" content=\"light dark\">")
            appendLine("<title>$pageTitle</title>")
            appendLine("<style>")
            appendLine(":root {")
            appendLine("  color-scheme: light dark;")
            appendLine("  --bg-page: #f8fafc;")
            appendLine("  --text-primary: #0f172a;")
            appendLine("  --text-secondary: #64748b;")
            appendLine("  --text-muted: #94a3b8;")
            appendLine("  --card-bg: #ffffff;")
            appendLine("  --card-border: #e2e8f0;")
            appendLine("  --card-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.05);")
            appendLine("  --box-bg: #f8fafc;")
            appendLine("  --box-border: #e2e8f0;")
            appendLine("  --table-header-bg: #f1f5f9;")
            appendLine("  --table-header-text: #475569;")
            appendLine("  --table-row-even: #fbfcfe;")
            appendLine("  --table-border: #f1f5f9;")
            appendLine("  --badge-ess-bg: #e0f2fe;")
            appendLine("  --badge-ess-text: #0284c7;")
            appendLine("  --badge-manual-bg: #fef3c7;")
            appendLine("  --badge-manual-text: #b45309;")
            appendLine("  --badge-off-bg: #f1f5f9;")
            appendLine("  --badge-off-text: #64748b;")
            appendLine("  --badge-missing-bg: #fee2e2;")
            appendLine("  --badge-missing-text: #b91c1c;")
            appendLine("  --accent: #0284c7;")
            appendLine("  --accent-text: #ffffff;")
            appendLine("  --green: #16a34a;")
            appendLine("}")
            appendLine("@media (prefers-color-scheme: dark) {")
            appendLine("  :root {")
            appendLine("    --bg-page: #000000;")
            appendLine("    --text-primary: #f4f4f5;")
            appendLine("    --text-secondary: #a1a1aa;")
            appendLine("    --text-muted: #71717a;")
            appendLine("    --card-bg: #09090b;")
            appendLine("    --card-border: #27272a;")
            appendLine("    --card-shadow: 0 4px 12px rgba(0, 0, 0, 0.8);")
            appendLine("    --box-bg: #000000;")
            appendLine("    --box-border: #27272a;")
            appendLine("    --table-header-bg: #141416;")
            appendLine("    --table-header-text: #cbd5e1;")
            appendLine("    --table-row-even: #050506;")
            appendLine("    --table-border: #1e1e24;")
            appendLine("    --badge-ess-bg: #082f49;")
            appendLine("    --badge-ess-text: #38bdf8;")
            appendLine("    --badge-manual-bg: #451a03;")
            appendLine("    --badge-manual-text: #fbbf24;")
            appendLine("    --badge-off-bg: #18181b;")
            appendLine("    --badge-off-text: #a1a1aa;")
            appendLine("    --badge-missing-bg: #450a0a;")
            appendLine("    --badge-missing-text: #f87171;")
            appendLine("    --accent: #38bdf8;")
            appendLine("    --accent-text: #000000;")
            appendLine("    --green: #4ade80;")
            appendLine("  }")
            appendLine("}")
            appendLine("* { box-sizing: border-box; }")
            appendLine("body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Cairo', 'Noto Sans Arabic', Tahoma, Arial, sans-serif; background: var(--bg-page); color: var(--text-primary); margin: 0; padding: 16px; direction: rtl; transition: background 0.2s, color 0.2s; }")
            appendLine(".container { max-width: 960px; margin: 0 auto; }")
            appendLine(".card { background: var(--card-bg); border-radius: 16px; padding: 20px; box-shadow: var(--card-shadow); margin-bottom: 16px; border: 1px solid var(--card-border); }")
            appendLine(".header-title { font-size: 22px; font-weight: 800; color: var(--text-primary); margin-bottom: 6px; letter-spacing: -0.3px; }")
            appendLine(".header-sub { font-size: 13px; color: var(--text-secondary); line-height: 1.5; }")
            appendLine(".grid-summary { display: grid; grid-template-columns: repeat(auto-fit, minmax(130px, 1fr)); gap: 10px; margin-top: 16px; }")
            appendLine(".summary-box { background: var(--box-bg); border-radius: 12px; padding: 12px 10px; text-align: center; border: 1px solid var(--box-border); }")
            appendLine(".summary-label { font-size: 11px; color: var(--text-secondary); margin-bottom: 5px; font-weight: 600; }")
            appendLine(".summary-val { font-size: 15px; font-weight: 800; color: var(--text-primary); }")
            appendLine(".summary-val.highlight { color: var(--accent); }")
            appendLine(".summary-val.green { color: var(--green); }")
            appendLine(".table-wrap { overflow-x: auto; border-radius: 12px; border: 1px solid var(--table-border); -webkit-overflow-scrolling: touch; }")
            appendLine("table { width: 100%; border-collapse: collapse; font-size: 13px; white-space: nowrap; }")
            appendLine("th { background: var(--table-header-bg); color: var(--table-header-text); padding: 11px 12px; text-align: right; border-bottom: 1px solid var(--card-border); font-weight: 700; font-size: 12px; }")
            appendLine("td { padding: 10px 12px; border-bottom: 1px solid var(--table-border); color: var(--text-primary); }")
            appendLine("tr:last-child td { border-bottom: none; }")
            appendLine("tr:nth-child(even) td { background: var(--table-row-even); }")
            appendLine(".badge { display: inline-block; padding: 3px 8px; border-radius: 6px; font-size: 11px; font-weight: 700; }")
            appendLine(".badge-ess { background: var(--badge-ess-bg); color: var(--badge-ess-text); }")
            appendLine(".badge-manual { background: var(--badge-manual-bg); color: var(--badge-manual-text); }")
            appendLine(".badge-off { background: var(--badge-off-bg); color: var(--badge-off-text); }")
            appendLine(".badge-missing { background: var(--badge-missing-bg); color: var(--badge-missing-text); }")
            appendLine("@media print {")
            appendLine("  body { background: #ffffff !important; color: #000000 !important; padding: 0 !important; }")
            appendLine("  .container { max-width: 100% !important; margin: 0 !important; }")
            appendLine("  .card { background: #ffffff !important; box-shadow: none !important; border: 1px solid #d1d5db !important; margin-bottom: 12px !important; }")
            appendLine("  .summary-box { background: #f9fafb !important; border: 1px solid #e5e7eb !important; }")
            appendLine("  .table-wrap { border: 1px solid #d1d5db !important; }")
            appendLine("  th { background: #f3f4f6 !important; color: #000000 !important; border-bottom: 2px solid #9ca3af !important; }")
            appendLine("  td { border-bottom: 1px solid #e5e7eb !important; color: #000000 !important; }")
            appendLine("  tr:nth-child(even) td { background: #f9fafb !important; }")
            appendLine("  .badge { border: 1px solid #9ca3af !important; }")
            appendLine("}")
            appendLine("</style>")
            appendLine("</head>")
            appendLine("<body>")
            appendLine("<div class=\"container\">")
            appendLine("<div class=\"card\">")
            appendLine("<div class=\"header-title\">$pageTitle</div>")
            appendLine("<div class=\"header-sub\">الفترة: <b>من $periodStart إلى $periodEnd</b></div>")
            val rateDisplay = "${if (hourlyRate % 1.0 == 0.0) hourlyRate.toLong().toString() else String.format(Locale.US, "%.2f", hourlyRate)} ج"
            appendLine("<div class=\"grid-summary\">")
            appendLine("<div class=\"summary-box\"><div class=\"summary-label\">نظام العمل</div><div class=\"summary-val\">${if (isFixedEmployment) "نظام التعيين" else "نظام الساعة"}</div></div>")
            appendLine("<div class=\"summary-box\"><div class=\"summary-label\">سعر الساعة</div><div class=\"summary-val\">$rateDisplay</div></div>")
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
            appendLine("<div class=\"header-title\" style=\"font-size: 16px; margin-bottom: 12px;\">سجل الورديات (${sortedRecords.size})</div>")
            appendLine("<div class=\"table-wrap\">")
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
                appendLine("<td style=\"color: var(--text-secondary);\">${allNotes.ifBlank { "-" }}</td>")
                appendLine("</tr>")
            }

            appendLine("</tbody>")
            appendLine("</table>")
            appendLine("</div>")
            appendLine("</div>")
            appendLine("</div>") // close container

            appendLine("</body>")
            appendLine("</html>")
        }

        saveToPublicDownloads(context, fileName, html, "text/html").getOrThrow()
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

    fun getExportDir(context: Context): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val shiftatiFolder = File(publicDownloads, "Shiftati")
        if (!shiftatiFolder.exists()) {
            shiftatiFolder.mkdirs()
        }
        return shiftatiFolder
    }

    /**
     * Saves content to the public Downloads/Shiftati folder.
     * Uses direct File writing + MediaScanner, falling back to MediaStore.Downloads (API 29+),
     * and finally app external/internal storage if completely blocked.
     */
    fun saveToPublicDownloads(
        context: Context,
        fileName: String,
        content: String,
        mimeType: String,
        subFolder: String = "Shiftati"
    ): Result<String> = runCatching {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = File(publicDownloads, subFolder)

        // 1. Direct POSIX File writing to public Downloads/Shiftati
        try {
            if (!targetDir.exists()) {
                targetDir.mkdirs()
            }
            val targetFile = File(targetDir, fileName)
            FileOutputStream(targetFile).use { fos ->
                OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            }
            // Index file into Android system media & download manager
            try {
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(mimeType),
                    null
                )
            } catch (_: Exception) {}

            return@runCatching targetFile.absolutePath
        } catch (_: Exception) {
            // Direct write failed, fallback to MediaStore on Android 10+
        }

        // 2. MediaStore.Downloads (Android 10+ / API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$subFolder")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { os ->
                        OutputStreamWriter(os, Charsets.UTF_8).use { writer ->
                            writer.write(content)
                            writer.flush()
                        }
                    }
                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, contentValues, null, null)

                    val targetFile = File(targetDir, fileName)
                    if (targetFile.exists()) {
                        return@runCatching targetFile.absolutePath
                    }
                    return@runCatching uri.toString()
                }
            } catch (_: Exception) {
                // MediaStore failed, proceed to fallback
            }
        }

        // 3. Fallback to app external files dir or internal files
        val fallbackDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.getExternalFilesDir(null)
            ?: File(context.filesDir, "Shiftati")
        if (!fallbackDir.exists()) fallbackDir.mkdirs()
        val fallbackFile = File(fallbackDir, fileName)
        FileOutputStream(fallbackFile).use { fos ->
            OutputStreamWriter(fos, Charsets.UTF_8).use { writer ->
                writer.write(content)
                writer.flush()
            }
        }
        fallbackFile.absolutePath
    }

    /**
     * Open exported file in browser or default viewer app using ACTION_VIEW intent
     */
    fun openExportedFile(context: Context, filePath: String) {
        try {
            val uri: Uri
            val mimeType = when {
                filePath.endsWith(".html", ignoreCase = true) -> "text/html"
                filePath.endsWith(".csv", ignoreCase = true) -> "text/csv"
                filePath.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                filePath.endsWith(".json", ignoreCase = true) -> "application/json"
                else -> "*/*"
            }

            if (filePath.startsWith("content://")) {
                uri = Uri.parse(filePath)
            } else {
                val file = File(filePath)
                if (!file.exists()) return

                uri = try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } catch (_: Exception) {
                    Uri.fromFile(file)
                }
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                val resInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (_: Exception) {}

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
            val uri: Uri
            val mimeType = when {
                filePath.endsWith(".csv", ignoreCase = true) -> "text/csv"
                filePath.endsWith(".html", ignoreCase = true) -> "text/html"
                filePath.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
                filePath.endsWith(".json", ignoreCase = true) -> "application/json"
                else -> "*/*"
            }

            if (filePath.startsWith("content://")) {
                uri = Uri.parse(filePath)
            } else {
                val file = File(filePath)
                if (!file.exists()) return

                uri = try {
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                } catch (_: Exception) {
                    Uri.fromFile(file)
                }
            }

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "تقرير حضور وانصراف - شفتاتي")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            try {
                val resInfoList = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                for (resolveInfo in resInfoList) {
                    val packageName = resolveInfo.activityInfo.packageName
                    context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (_: Exception) {}

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

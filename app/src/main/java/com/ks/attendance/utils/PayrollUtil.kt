package com.ks.attendance.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * PayrollUtil — calculates the payroll period boundaries.
 * Direct Kotlin port of payroll_period() from 1.py.
 *
 * Default: cycle starts on day [startDay] of each month through day [startDay]-1 of the next.
 * Example: startDay=29 → 29 Oct – 28 Nov.
 */
object PayrollUtil {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Returns (periodStart, periodEnd) as "yyyy-MM-dd" strings
     * for the payroll period that contains [referenceDate].
     */
    fun payrollPeriod(
        referenceDate: Date = Date(),
        startDay: Int = 29
    ): Pair<String, String> {
        require(startDay in 1..31) { "startDay must be 1..31" }

        val cal = Calendar.getInstance()

        fun monthAnchor(year: Int, month: Int): Calendar {
            val c = Calendar.getInstance()
            c.set(year, month, 1)
            val lastDay = c.getActualMaximum(Calendar.DAY_OF_MONTH)
            c.set(Calendar.DAY_OF_MONTH, minOf(startDay, lastDay))
            return c
        }

        cal.time = referenceDate
        val refYear = cal.get(Calendar.YEAR)
        val refMonth = cal.get(Calendar.MONTH)   // 0-indexed

        var anchor = monthAnchor(refYear, refMonth)

        // If reference day < anchor day, step back one month
        if (referenceDate.before(anchor.time)) {
            val prevMonth = if (refMonth == 0) 11 else refMonth - 1
            val prevYear = if (refMonth == 0) refYear - 1 else refYear
            anchor = monthAnchor(prevYear, prevMonth)
        }

        // Next anchor = anchor.month + 1
        val nextMonth = if (anchor.get(Calendar.MONTH) == 11) 0
                        else anchor.get(Calendar.MONTH) + 1
        val nextYear = if (anchor.get(Calendar.MONTH) == 11) anchor.get(Calendar.YEAR) + 1
                       else anchor.get(Calendar.YEAR)
        val nextAnchor = monthAnchor(nextYear, nextMonth)

        // Period end = day before next anchor
        nextAnchor.add(Calendar.DAY_OF_YEAR, -1)
        val periodEnd = nextAnchor

        return sdf.format(anchor.time) to sdf.format(periodEnd.time)
    }

    /** Parse a "yyyy-MM-dd" string to Date */
    fun parseDate(s: String): Date = sdf.parse(s)!!

    /** Format epoch millis to "yyyy-MM-dd" */
    fun formatDate(millis: Long): String = sdf.format(Date(millis))

    /** Format epoch millis to "HH:mm" */
    fun formatTime(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

    /** Format epoch millis to "HH:mm:ss" */
    fun formatTimeFull(millis: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(millis))

    /** Format epoch millis to "dd MMM yyyy, HH:mm" */
    fun formatDateTime(millis: Long): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("ar")).format(Date(millis))

    /** Calculate hours between two epoch-millis values */
    fun hoursWorked(checkIn: Long, checkOut: Long): Double =
        (checkOut - checkIn).toDouble() / 3_600_000.0

    /** Format hours as "X ساعة Y دقيقة" */
    fun formatHours(hours: Double): String {
        val totalMinutes = (hours * 60).toLong()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0L -> "$m دقيقة"
            m == 0L -> "$h ساعة"
            else -> "$h ساعة $m دقيقة"
        }
    }

    /** Format hours as "X.XX hrs" for English mode */
    fun formatHoursEn(hours: Double): String {
        val totalMinutes = (hours * 60).toLong()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }
}

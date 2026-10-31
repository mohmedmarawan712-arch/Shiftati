package com.ks.attendance.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * PayrollUtil — calculates dynamic, calendar-accurate payroll periods.
 * Supports:
 * - Current calendar month (1st to actual last day: 28, 29, 30, or 31).
 * - Previous calendar month (1st to actual last day of previous month).
 * - Both months (1st of previous month to end of current month).
 * - Custom cutoff day (e.g. from day 26 to 25, or 29 to 28, dynamically bounded).
 */
object PayrollUtil {

    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    const val CYCLE_29_TO_28       = "CYCLE_29_TO_28"
    const val CYCLE_CURRENT_MONTH  = "CURRENT_MONTH"
    const val CYCLE_PREVIOUS_MONTH = "PREVIOUS_MONTH"
    const val CYCLE_BOTH_MONTHS    = "BOTH_MONTHS"
    const val CYCLE_CUSTOM_DAY     = "CUSTOM_DAY"

    /**
     * Resolves the period according to [cycleType] and [startDay].
     * Defaults to the calendar-accurate [CYCLE_29_TO_28] (day 29 to day 28).
     */
    fun resolvePeriod(
        cycleType: String = CYCLE_29_TO_28,
        startDay: Int = 29,
        referenceDate: Date = Date()
    ): Pair<String, String> {
        return when (cycleType) {
            CYCLE_CURRENT_MONTH  -> getCurrentMonthPeriod(referenceDate)
            CYCLE_PREVIOUS_MONTH -> getPreviousMonthPeriod(referenceDate)
            CYCLE_BOTH_MONTHS    -> getBothMonthsPeriod(referenceDate)
            CYCLE_CUSTOM_DAY     -> getCustomDayPeriod(startDay, referenceDate)
            else                 -> getPayrollPeriod29to28(referenceDate)
        }
    }

    /**
     * Default automated cycle: Day 29 through Day 28 of following period,
     * fully respecting variable month lengths (31-day months, 28/29 in Feb).
     * Mirrors the Python payroll_period(reference, 29) behavior.
     */
    fun getPayrollPeriod29to28(referenceDate: Date = Date()): Pair<String, String> {
        val cal = Calendar.getInstance().apply { time = referenceDate }
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) // 0-indexed

        fun monthAnchor(year: Int, month: Int): Calendar {
            val c = Calendar.getInstance()
            c.set(Calendar.YEAR, year)
            c.set(Calendar.MONTH, month)
            val maxDay = c.getActualMaximum(Calendar.DAY_OF_MONTH)
            c.set(Calendar.DAY_OF_MONTH, minOf(29, maxDay))
            return c
        }

        var anchor = monthAnchor(currentYear, currentMonth)
        if (cal.before(anchor)) {
            val prevCal = Calendar.getInstance().apply {
                set(Calendar.YEAR, currentYear)
                set(Calendar.MONTH, currentMonth)
                add(Calendar.MONTH, -1)
            }
            anchor = monthAnchor(prevCal.get(Calendar.YEAR), prevCal.get(Calendar.MONTH))
        }

        val nextCal = Calendar.getInstance().apply {
            set(Calendar.YEAR, anchor.get(Calendar.YEAR))
            set(Calendar.MONTH, anchor.get(Calendar.MONTH))
            add(Calendar.MONTH, 1)
        }
        val nextAnchor = monthAnchor(nextCal.get(Calendar.YEAR), nextCal.get(Calendar.MONTH))
        nextAnchor.add(Calendar.DAY_OF_YEAR, -1)

        return sdf.format(anchor.time) to sdf.format(nextAnchor.time)
    }

    /** 1st to last calendar day of the month containing [referenceDate] */
    fun getCurrentMonthPeriod(referenceDate: Date = Date()): Pair<String, String> {
        val cal = Calendar.getInstance().apply { time = referenceDate }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = sdf.format(cal.time)

        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, maxDay)
        val end = sdf.format(cal.time)

        return start to end
    }

    /** 1st to last calendar day of the month prior to [referenceDate] */
    fun getPreviousMonthPeriod(referenceDate: Date = Date()): Pair<String, String> {
        val cal = Calendar.getInstance().apply {
            time = referenceDate
            add(Calendar.MONTH, -1)
        }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = sdf.format(cal.time)

        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, maxDay)
        val end = sdf.format(cal.time)

        return start to end
    }

    /** From 1st of previous month to last day of current month */
    fun getBothMonthsPeriod(referenceDate: Date = Date()): Pair<String, String> {
        val prevStart = getPreviousMonthPeriod(referenceDate).first
        val currEnd   = getCurrentMonthPeriod(referenceDate).second
        return prevStart to currEnd
    }

    /**
     * Custom cycle starting on day [startDay] (e.g. 26).
     * Bounded dynamically by each month's actual length.
     */
    fun getCustomDayPeriod(
        startDay: Int,
        referenceDate: Date = Date()
    ): Pair<String, String> {
        val day = startDay.coerceIn(1, 31)
        if (day == 1) return getCurrentMonthPeriod(referenceDate)

        val cal = Calendar.getInstance().apply { time = referenceDate }
        val currentDay = cal.get(Calendar.DAY_OF_MONTH)

        // If today is before the startDay, period started in previous month
        if (currentDay < day) {
            cal.add(Calendar.MONTH, -1)
        }

        // Anchor start date
        val startMax = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, minOf(day, startMax))
        val start = sdf.format(cal.time)

        // End is in next month, day before next startDay
        cal.add(Calendar.MONTH, 1)
        val endMax = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, minOf(day, endMax))
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val end = sdf.format(cal.time)

        return start to end
    }

    /** Backward-compatible helper */
    fun payrollPeriod(
        referenceDate: Date = Date(),
        startDay: Int = 1
    ): Pair<String, String> = resolvePeriod(
        if (startDay == 1) CYCLE_CURRENT_MONTH else CYCLE_CUSTOM_DAY,
        startDay,
        referenceDate
    )

    fun parseDate(s: String): Date = sdf.parse(s) ?: Date()
    fun formatDate(millis: Long): String = sdf.format(Date(millis))
    fun formatTime(millis: Long, is24Hour: Boolean = false): String {
        return if (is24Hour) {
            SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))
        } else {
            val raw = SimpleDateFormat("h:mm a", Locale.US).format(Date(millis))
            raw.replace("AM", "ص").replace("PM", "م")
        }
    }
    fun formatTimeFull(millis: Long, is24Hour: Boolean = false): String {
        return if (is24Hour) {
            SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(millis))
        } else {
            val raw = SimpleDateFormat("h:mm:ss a", Locale.US).format(Date(millis))
            raw.replace("AM", "ص").replace("PM", "م")
        }
    }

    fun hoursWorked(checkIn: Long, checkOut: Long): Double =
        (checkOut - checkIn).toDouble() / 3_600_000.0

    fun formatHours(hours: Double): String {
        val totalMinutes = (hours * 60).toLong()
        val h = totalMinutes / 60
        val m = totalMinutes % 60
        return when {
            h == 0L -> "$m دقيقة"
            m == 0L -> "$h ساعة"
            else -> "$h س $m د"
        }
    }

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

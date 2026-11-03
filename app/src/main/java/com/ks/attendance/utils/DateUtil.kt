package com.ks.attendance.utils

import java.text.SimpleDateFormat
import java.util.*

/**
 * DateUtil — Arabic and English date/time helpers.
 */
object DateUtil {

    private val arabicMonths = listOf(
        "يناير", "فبراير", "مارس", "إبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
    )

    private val arabicDays = listOf(
        "الاثنين", "الثلاثاء", "الأربعاء",
        "الخميس", "الجمعة", "السبت", "الأحد"
    )

    private val sdfThreadLocal = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }

    fun arabicDayName(dayOfWeek: Int): String = arabicDays.getOrElse(dayOfWeek) { "" }

    /** Format Long millis to "yyyy-MM-dd" */
    fun formatDate(millis: Long): String =
        sdfThreadLocal.get()!!.format(Date(millis))

    /** "yyyy-MM-dd" → Calendar */
    fun parseToCalendar(dateStr: String): Calendar {
        val cal = Calendar.getInstance()
        sdfThreadLocal.get()!!.parse(dateStr)?.let { cal.time = it }
        return cal
    }

    /** "yyyy-MM-dd" → Arabic formatted like "الاثنين، 28 أكتوبر 2026" */
    fun toArabicFull(dateStr: String): String {
        val cal = parseToCalendar(dateStr)
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        // Convert Calendar.DAY_OF_WEEK (1=Sun, 2=Mon, ... 7=Sat) to our index (Mon=0 .. Sun=6)
        val dayIndex = when (dayOfWeek) {
            Calendar.MONDAY    -> 0
            Calendar.TUESDAY   -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY  -> 3
            Calendar.FRIDAY    -> 4
            Calendar.SATURDAY  -> 5
            Calendar.SUNDAY    -> 6
            else -> 0
        }
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = arabicMonths[cal.get(Calendar.MONTH)]
        val year = cal.get(Calendar.YEAR)
        return "${arabicDays[dayIndex]}، $day $month $year"
    }

    /** Today as "yyyy-MM-dd" */
    fun today(): String = formatDate(System.currentTimeMillis())

    private val dateTimeThreadLocal = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    }

    /** "yyyy-MM-dd" + hour + minute → Long millis */
    fun dateTimeToMillis(date: String, hour: Int, minute: Int): Long {
        return dateTimeThreadLocal.get()!!.parse("$date %02d:%02d".format(hour, minute))?.time ?: System.currentTimeMillis()
    }

    /** Returns all dates in a month as "yyyy-MM-dd" list */
    fun datesInMonth(year: Int, month: Int): List<String> {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val sdf = sdfThreadLocal.get()!!
        return (1..lastDay).map { day ->
            cal.set(Calendar.DAY_OF_MONTH, day)
            sdf.format(cal.time)
        }
    }

    /** Day of week (0=Monday, 6=Sunday) for given "yyyy-MM-dd" */
    fun dayOfWeekIndex(dateStr: String): Int {
        val cal = parseToCalendar(dateStr)
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY    -> 0
            Calendar.TUESDAY   -> 1
            Calendar.WEDNESDAY -> 2
            Calendar.THURSDAY  -> 3
            Calendar.FRIDAY    -> 4
            Calendar.SATURDAY  -> 5
            Calendar.SUNDAY    -> 6
            else -> 0
        }
    }

    /** Convert UTC millis (from Compose DatePicker / DateRangePicker) to "yyyy-MM-dd" */
    fun utcMillisToDate(utcMillis: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = utcMillis
        return "%04d-%02d-%02d".format(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
    }

    /** Convert "yyyy-MM-dd" to UTC millis at 00:00:00 UTC */
    fun dateToUtcMillis(dateStr: String): Long {
        val parts = dateStr.trim().split("-")
        if (parts.size != 3) return System.currentTimeMillis()
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt(), 0, 0, 0)
        return cal.timeInMillis
    }

    /** Calculates number of inclusive days between startStr and endStr ("yyyy-MM-dd") */
    fun calculateDaysCount(startStr: String, endStr: String): Int {
        if (startStr.isBlank() || endStr.isBlank()) return 0
        return try {
            val s = sdfThreadLocal.get()!!.parse(startStr)?.time ?: return 0
            val e = sdfThreadLocal.get()!!.parse(endStr)?.time ?: return 0
            val diff = kotlin.math.abs(e - s)
            (diff / (24 * 3600 * 1000L)).toInt() + 1
        } catch (_: Exception) {
            0
        }
    }

    /** Returns Pair(start, end) for the last 30 days */
    fun getLast30DaysPeriod(): Pair<String, String> {
        val end = today()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -29)
        return formatDate(cal.timeInMillis) to end
    }

    /** Returns Pair(start, end) for the last 14 days */
    fun getLast14DaysPeriod(): Pair<String, String> {
        val end = today()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -13)
        return formatDate(cal.timeInMillis) to end
    }

    /** Returns Pair(start, end) for the last 7 days */
    fun getLast7DaysPeriod(): Pair<String, String> {
        val end = today()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        return formatDate(cal.timeInMillis) to end
    }
}

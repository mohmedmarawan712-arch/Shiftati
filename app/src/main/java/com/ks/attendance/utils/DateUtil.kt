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
}

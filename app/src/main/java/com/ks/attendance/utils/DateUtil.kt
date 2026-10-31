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

    private val arabicDaysShort = listOf("الإثن", "الثلا", "الأرب", "الخمي", "الجمع", "السبت", "الأحد")

    private val calHeaders = listOf("س", "أ", "ن", "ث", "ر", "خ", "ج")   // Sat=0 … Fri=6 in KSA week

    fun arabicMonthName(month: Int): String = arabicMonths.getOrElse(month) { "" }  // 0-indexed

    fun arabicDayName(dayOfWeek: Int): String = arabicDays.getOrElse(dayOfWeek) { "" }  // Calendar.MONDAY=2 → index 0

    fun calendarHeaderLetters(): List<String> = calHeaders

    /** Format Long millis to "HH:mm" */
    fun formatHHmm(millis: Long): String =
        SimpleDateFormat("HH:mm", Locale.US).format(Date(millis))

    /** Format Long millis to "HH:mm:ss" */
    fun formatHHmmss(millis: Long): String =
        SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(millis))

    /** Format Long millis to "yyyy-MM-dd" */
    fun formatDate(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    /** "yyyy-MM-dd" → Calendar */
    fun parseToCalendar(dateStr: String): Calendar {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        sdf.parse(dateStr)?.let { cal.time = it }
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

    /** Returns all dates in a month as "yyyy-MM-dd" list */
    fun datesInMonth(year: Int, month: Int): List<String> {
        val cal = Calendar.getInstance()
        cal.set(year, month, 1)
        val lastDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
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

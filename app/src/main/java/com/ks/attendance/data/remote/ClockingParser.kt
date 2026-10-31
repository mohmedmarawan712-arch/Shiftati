package com.ks.attendance.data.remote

import java.text.SimpleDateFormat
import java.util.*

/**
 * ClockingParser — parses raw ESS clocking items and pairs them into shifts.
 * Direct Kotlin port of parse_clocking() + pair_shifts() from 1.py.
 */
object ClockingParser {

    data class ParsedClocking(
        val timestamp: Date,
        val eventType: String // "ClockIn" | "ClockOut"
    )

    data class Shift(
        val start: Date,
        val end: Date,
        val hoursWorked: Double,
        val workDate: String  // "yyyy-MM-dd"
    )

    data class UnmatchedEvent(
        val type: String,
        val time: Date,
        val note: String
    )

    private val DATE_FORMATS = listOf(
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss.SSS", Locale.US),
        SimpleDateFormat("yyyy/MM/dd HH:mm:ss", Locale.US)
    )
    private val OUTPUT_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun parse(item: ClockingItem): ParsedClocking? {
        val raw = item.clockedTime ?: return null
        val event = item.eventType ?: return null
        if (event !in listOf("ClockIn", "ClockOut")) return null

        val status = item.status?.toString()?.lowercase() ?: ""
        if (status.isNotEmpty() && status !in listOf("approve", "approved", "0", "")) return null

        val stamp = DATE_FORMATS.firstNotNullOfOrNull { fmt ->
            try { fmt.parse(raw.take(if (fmt.toPattern().contains("SSS")) 23 else 19)) }
            catch (_: Exception) { null }
        } ?: return null

        return ParsedClocking(stamp, event)
    }

    fun pairShifts(clockings: List<ClockingItem>): Pair<List<Shift>, List<UnmatchedEvent>> {
        val parsed = clockings.mapNotNull { parse(it) }.sortedBy { it.timestamp }
        val shifts = mutableListOf<Shift>()
        val unmatched = mutableListOf<UnmatchedEvent>()
        var openIn: Date? = null

        for ((stamp, event) in parsed) {
            when (event) {
                "ClockIn" -> {
                    if (openIn != null) {
                        unmatched += UnmatchedEvent(
                            type = "ClockIn بدون إغلاق",
                            time = openIn!!,
                            note = "تسجيل دخول جديد قبل تسجيل خروج السابق"
                        )
                    }
                    openIn = stamp
                }
                "ClockOut" -> {
                    if (openIn == null) {
                        unmatched += UnmatchedEvent(
                            type = "ClockOut بدون ClockIn",
                            time = stamp,
                            note = "لا يوجد تسجيل دخول مسبق"
                        )
                        continue
                    }
                    if (!stamp.after(openIn)) {
                        unmatched += UnmatchedEvent(
                            type = "توقيت غير منطقي",
                            time = stamp,
                            note = "وقت الخروج أقدم من وقت الدخول"
                        )
                        openIn = null
                        continue
                    }
                    val hours = (stamp.time - openIn!!.time).toDouble() / 3_600_000.0
                    shifts += Shift(
                        start = openIn!!,
                        end = stamp,
                        hoursWorked = hours,
                        workDate = OUTPUT_DATE_FORMAT.format(openIn!!)
                    )
                    openIn = null
                }
            }
        }

        if (openIn != null) {
            unmatched += UnmatchedEvent(
                type = "ClockIn بدون ClockOut",
                time = openIn!!,
                note = "لم يظهر تسجيل خروج مقابل"
            )
        }

        return shifts to unmatched
    }
}

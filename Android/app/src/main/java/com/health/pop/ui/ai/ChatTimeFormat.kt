package com.health.pop.ui.ai

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Định dạng giờ/ngày cho khung chat. Locale.US để luôn dùng chữ số Latin. */
internal object ChatTimeFormat {

    fun time(millis: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = zone }.format(Date(millis))

    /** Khoá theo ngày lịch dạng yyyyMMdd, dùng để biết hai tin có cùng ngày hay không. */
    fun dayKey(millis: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = millis }
        return calendar.get(Calendar.YEAR) * 10_000 +
            (calendar.get(Calendar.MONTH) + 1) * 100 +
            calendar.get(Calendar.DAY_OF_MONTH)
    }

    fun dayLabel(millis: Long, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): String {
        val key = dayKey(millis, zone)
        val today = dayKey(nowMillis, zone)
        val yesterday = dayKey(
            Calendar.getInstance(zone).apply {
                timeInMillis = nowMillis
                add(Calendar.DAY_OF_YEAR, -1)
            }.timeInMillis,
            zone
        )
        return when (key) {
            today -> "Hôm nay"
            yesterday -> "Hôm qua"
            else -> SimpleDateFormat("dd/MM/yyyy", Locale.US).apply { timeZone = zone }.format(Date(millis))
        }
    }
}

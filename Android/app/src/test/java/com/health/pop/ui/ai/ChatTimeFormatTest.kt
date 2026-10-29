package com.health.pop.ui.ai

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ChatTimeFormatTest {

    private val vietnam = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: TimeZone = vietnam): Long =
        Calendar.getInstance(zone).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    @Test
    fun time_formatsHourAndMinuteInGivenZone() {
        assertEquals("07:05", ChatTimeFormat.time(millis(2026, 10, 26, 7, 5), vietnam))
        assertEquals("23:59", ChatTimeFormat.time(millis(2026, 10, 26, 23, 59), vietnam))
    }

    @Test
    fun dayKey_isCalendarDayInZone() {
        assertEquals(20261026, ChatTimeFormat.dayKey(millis(2026, 10, 26, 0, 1), vietnam))
        assertEquals(20261026, ChatTimeFormat.dayKey(millis(2026, 10, 26, 23, 59), vietnam))
        assertNotEquals(
            ChatTimeFormat.dayKey(millis(2026, 10, 26, 23, 59), vietnam),
            ChatTimeFormat.dayKey(millis(2026, 10, 27, 0, 1), vietnam)
        )
    }

    @Test
    fun dayKey_sameInstantDiffersAcrossTimeZones() {
        val instant = millis(2026, 10, 26, 23, 30, vietnam) // 16:30 UTC cùng ngày
        val utc = TimeZone.getTimeZone("UTC")
        val tokyo = TimeZone.getTimeZone("Asia/Tokyo") // 01:30 ngày hôm sau
        assertEquals(20261026, ChatTimeFormat.dayKey(instant, utc))
        assertEquals(20261027, ChatTimeFormat.dayKey(instant, tokyo))
    }

    @Test
    fun dayLabel_todayYesterdayAndOlderDates() {
        val now = millis(2026, 10, 26, 15, 0)
        assertEquals("Hôm nay", ChatTimeFormat.dayLabel(millis(2026, 10, 26, 0, 5), now, vietnam))
        assertEquals("Hôm qua", ChatTimeFormat.dayLabel(millis(2026, 10, 25, 23, 59), now, vietnam))
        assertEquals("24/10/2026", ChatTimeFormat.dayLabel(millis(2026, 10, 24, 12, 0), now, vietnam))
        assertEquals("31/12/2025", ChatTimeFormat.dayLabel(millis(2025, 12, 31, 12, 0), now, vietnam))
    }

    @Test
    fun dayLabel_yesterdayAcrossMonthBoundary() {
        val now = millis(2026, 11, 1, 9, 0)
        assertEquals("Hôm qua", ChatTimeFormat.dayLabel(millis(2026, 10, 31, 20, 0), now, vietnam))
    }
}

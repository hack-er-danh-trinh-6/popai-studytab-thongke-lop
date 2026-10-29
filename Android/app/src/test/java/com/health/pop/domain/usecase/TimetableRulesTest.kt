package com.health.pop.domain.usecase

import com.health.pop.domain.model.TimetableEntry
import com.health.pop.domain.model.TimetableSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TimetableRulesTest {

    private val entries = listOf(
        TimetableEntry(3, TimetableSession.AFTERNOON, 1, "Sử"),
        TimetableEntry(3, TimetableSession.MORNING, 2, "Văn"),
        TimetableEntry(3, TimetableSession.MORNING, 1, "Toán"),
        TimetableEntry(2, TimetableSession.MORNING, 1, "Anh")
    )

    @Test
    fun weekendHasNoSchoolDayAndDefaultsToMonday() {
        assertNull(TimetableRules.schoolWeekday(1)) // Chủ nhật
        assertNull(TimetableRules.schoolWeekday(7)) // Thứ 7
        assertEquals(4, TimetableRules.schoolWeekday(4))
        assertEquals(2, TimetableRules.defaultDay(1))
        assertEquals(2, TimetableRules.defaultDay(7))
        assertEquals(5, TimetableRules.defaultDay(5))
    }

    @Test
    fun entriesAreSortedBySessionThenPeriod() {
        val result = TimetableRules.entriesForDay(entries, 3).map { it.subject }
        assertEquals(listOf("Toán", "Văn", "Sử"), result)
    }

    @Test
    fun groupsBySessionAndSkipsEmptySessions() {
        val groups = TimetableRules.groupBySession(entries, 3)
        assertEquals(listOf(TimetableSession.MORNING, TimetableSession.AFTERNOON), groups.map { it.first })
        assertEquals(listOf("Toán", "Văn"), groups[0].second.map { it.subject })
        val monday = TimetableRules.groupBySession(entries, 2)
        assertEquals(1, monday.size)
        assertEquals(emptyList<Any>(), TimetableRules.groupBySession(entries, 6))
    }

    @Test
    fun countsIncludeEmptyDays() {
        val counts = TimetableRules.countsByDay(entries)
        assertEquals(mapOf(2 to 1, 3 to 3, 4 to 0, 5 to 0, 6 to 0), counts)
    }

    @Test
    fun labels() {
        assertEquals("Thứ 4", TimetableRules.dayLabel(4))
        assertEquals("Buổi sáng", TimetableRules.sessionLabel(TimetableSession.MORNING))
    }
}

package com.health.pop.domain.usecase

import com.health.pop.domain.model.TimetableEntry
import com.health.pop.domain.model.TimetableSession

/** Quy tắc hiển thị thời khóa biểu (thuần Kotlin, có test). Ngày học theo trường: 2 = Thứ 2 ... 6 = Thứ 6. */
object TimetableRules {

    val SCHOOL_DAYS = listOf(2, 3, 4, 5, 6)

    /**
     * Đổi `Calendar.DAY_OF_WEEK` (CN = 1, Thứ 2 = 2 ... Thứ 7 = 7) sang ngày học của trường.
     * Thứ 2 đến Thứ 6 trùng số; Thứ 7 và Chủ nhật không có tiết nên trả về null.
     */
    fun schoolWeekday(calendarDayOfWeek: Int): Int? = calendarDayOfWeek.takeIf { it in SCHOOL_DAYS }

    /** Ngày nên mở sẵn: hôm nay nếu là ngày học, ngược lại là Thứ 2 (tuần tới). */
    fun defaultDay(calendarDayOfWeek: Int): Int = schoolWeekday(calendarDayOfWeek) ?: SCHOOL_DAYS.first()

    fun dayLabel(weekday: Int): String = "Thứ $weekday"

    fun sessionLabel(session: TimetableSession): String = when (session) {
        TimetableSession.MORNING -> "Buổi sáng"
        TimetableSession.AFTERNOON -> "Buổi chiều"
    }

    /** Các tiết của một ngày, xếp theo buổi (sáng trước chiều) rồi theo số tiết. */
    fun entriesForDay(entries: List<TimetableEntry>, weekday: Int): List<TimetableEntry> =
        entries.filter { it.weekday == weekday }
            .sortedWith(compareBy({ it.session.ordinal }, { it.period }))

    /** Nhóm các tiết của một ngày theo buổi; buổi nào trống thì bỏ qua. */
    fun groupBySession(entries: List<TimetableEntry>, weekday: Int): List<Pair<TimetableSession, List<TimetableEntry>>> =
        entriesForDay(entries, weekday)
            .groupBy { it.session }
            .toList()
            .sortedBy { (session, _) -> session.ordinal }

    /** Số tiết của mỗi ngày học (kể cả ngày 0 tiết), để hiện chấm nhỏ trên nút chọn ngày. */
    fun countsByDay(entries: List<TimetableEntry>): Map<Int, Int> =
        SCHOOL_DAYS.associateWith { day -> entries.count { it.weekday == day } }
}

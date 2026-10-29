package com.health.pop.domain.usecase

/** Quy tắc chọn thời gian và tính streak của đồng hồ tập trung. */
object FocusStreakRules {
    /** Phiên hoàn thành kéo dài từ ngần này phút trở lên được tính 1 streak (khớp FOCUS_STREAK_MINUTES ở backend). */
    const val STREAK_MINUTES = 60
    const val MIN_TARGET_MINUTES = 5
    const val MAX_TARGET_MINUTES = 180
    const val STEP_MINUTES = 5
    val PRESET_MINUTES = listOf(15, 25, 45, 60, 90)

    fun earnsStreak(actualMinutes: Int): Boolean = actualMinutes >= STREAK_MINUTES

    /** Đưa số phút người dùng chọn về khoảng hợp lệ, làm tròn theo bước [STEP_MINUTES]. */
    fun clampTarget(minutes: Int): Int {
        val rounded = ((minutes + STEP_MINUTES / 2) / STEP_MINUTES) * STEP_MINUTES
        return rounded.coerceIn(MIN_TARGET_MINUTES, MAX_TARGET_MINUTES)
    }
}

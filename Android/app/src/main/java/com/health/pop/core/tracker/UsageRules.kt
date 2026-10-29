package com.health.pop.core.tracker

/** Quy tắc thuần (dễ test) quyết định khi nào cần thông báo. */
object UsageRules {

    const val STAGE_NONE = 0
    const val STAGE_WARNING = 1
    const val STAGE_OVER = 2

    /** Báo "sắp hết" khi đã dùng từ 80% hạn mức. */
    const val WARNING_RATIO = 0.8

    fun stageFor(usedMinutes: Long, limitMinutes: Int): Int = when {
        limitMinutes <= 0 -> STAGE_NONE
        usedMinutes >= limitMinutes -> STAGE_OVER
        // Hạn mức quá ngắn (< 5 phút) thì bỏ qua cảnh báo sớm.
        limitMinutes >= 5 && usedMinutes >= limitMinutes * WARNING_RATIO -> STAGE_WARNING
        else -> STAGE_NONE
    }

    /**
     * @param stage mốc hiện tại; @param lastStage mốc đã báo trong ngày.
     * Mốc vượt hạn còn được nhắc lại định kỳ nếu người dùng vẫn đang dùng.
     */
    fun shouldNotify(
        stage: Int,
        lastStage: Int,
        stillUsing: Boolean,
        nowMillis: Long,
        lastOverNotifiedAt: Long,
        repeatEveryMinutes: Int
    ): Boolean {
        if (stage == STAGE_NONE) return false
        if (stage > lastStage) return true
        if (stage == STAGE_OVER && stillUsing && repeatEveryMinutes > 0) {
            return nowMillis - lastOverNotifiedAt >= repeatEveryMinutes * 60_000L
        }
        return false
    }
}

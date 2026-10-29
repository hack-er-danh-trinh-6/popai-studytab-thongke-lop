package com.health.pop.core.tracker

import android.content.Context
import android.content.SharedPreferences
import java.time.LocalDate

/**
 * Lưu cấu hình tracker trên máy: bật/tắt, hạn mức phút/ngày cho từng app và trạng thái đã báo trong ngày
 * (để không gửi thông báo lặp lại mỗi lần kiểm tra).
 */
class UsageLimitStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var trackingEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Hạn mức phút/ngày; 0 nghĩa là chưa đặt (không theo dõi app này). */
    fun getLimitMinutes(app: TrackedApp): Int = prefs.getInt(limitKey(app), 0)

    fun setLimitMinutes(app: TrackedApp, minutes: Int) {
        prefs.edit().putInt(limitKey(app), minutes.coerceIn(0, MAX_LIMIT_MINUTES)).apply()
    }

    /** Đã gửi thông báo cho mốc [stage] của [app] trong ngày [day] chưa. Stage 1 = sắp hết (80%), 2 = vượt hạn. */
    fun lastNotifiedStage(app: TrackedApp, day: LocalDate = LocalDate.now()): Int {
        if (prefs.getString(dayKey(app), null) != day.toString()) return 0
        return prefs.getInt(stageKey(app), 0)
    }

    fun markNotified(app: TrackedApp, stage: Int, day: LocalDate = LocalDate.now()) {
        prefs.edit()
            .putString(dayKey(app), day.toString())
            .putInt(stageKey(app), stage)
            .apply()
    }

    /** Nhắc lại sau mỗi [repeatEveryMinutes] phút khi vẫn đang dùng quá hạn (0 = không nhắc lại). */
    fun lastOverNotifiedAt(app: TrackedApp): Long = prefs.getLong(overAtKey(app), 0L)

    fun markOverNotifiedAt(app: TrackedApp, timeMillis: Long) {
        prefs.edit().putLong(overAtKey(app), timeMillis).apply()
    }

    private fun limitKey(app: TrackedApp) = "limit_${app.key}"
    private fun dayKey(app: TrackedApp) = "notified_day_${app.key}"
    private fun stageKey(app: TrackedApp) = "notified_stage_${app.key}"
    private fun overAtKey(app: TrackedApp) = "over_at_${app.key}"

    companion object {
        private const val PREFS = "pop_usage_tracker"
        private const val KEY_ENABLED = "tracking_enabled"
        const val MAX_LIMIT_MINUTES = 24 * 60
        const val REPEAT_OVER_EVERY_MINUTES = 15
    }
}

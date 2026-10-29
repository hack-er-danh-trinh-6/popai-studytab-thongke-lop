package com.health.pop.core.tracker

import android.content.Context

/** Một lần kiểm tra: đọc thời gian dùng hôm nay, so với hạn mức và gửi thông báo nếu cần. */
object UsageTracker {

    fun check(context: Context, nowMillis: Long = System.currentTimeMillis()): UsageStatsReader.Snapshot? {
        val store = UsageLimitStore(context)
        if (!store.trackingEnabled || !UsageStatsReader.hasUsageAccess(context)) return null

        val snapshot = try {
            UsageStatsReader.readToday(context, nowMillis)
        } catch (_: SecurityException) {
            return null
        }

        TrackedApp.entries.forEach { app ->
            val limit = store.getLimitMinutes(app)
            val used = snapshot.minutesToday[app] ?: 0L
            val stage = UsageRules.stageFor(used, limit)
            val notify = UsageRules.shouldNotify(
                stage = stage,
                lastStage = store.lastNotifiedStage(app),
                stillUsing = snapshot.foregroundNow == app,
                nowMillis = nowMillis,
                lastOverNotifiedAt = store.lastOverNotifiedAt(app),
                repeatEveryMinutes = UsageLimitStore.REPEAT_OVER_EVERY_MINUTES
            )
            if (notify) {
                UsageNotificationHelper.showLimitNotification(context, app, used, limit, stage)
                store.markNotified(app, stage)
                if (stage == UsageRules.STAGE_OVER) store.markOverNotifiedAt(app, nowMillis)
            }
        }
        return snapshot
    }
}

package com.health.pop.core.tracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId

/** Đọc thời gian mở màn hình (foreground) của từng app trong ngày hôm nay bằng UsageStatsManager. */
object UsageStatsReader {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Số app trong [TrackedApp] đang được cài trên máy (dùng để báo "chưa cài TikTok" trên giao diện). */
    fun isInstalled(context: Context, app: TrackedApp): Boolean = app.packages.any { pkg ->
        try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    data class Snapshot(
        val minutesToday: Map<TrackedApp, Long>,
        /** App nào đang ở foreground ngay lúc này (nếu thuộc danh sách theo dõi). */
        val foregroundNow: TrackedApp?
    )

    /**
     * Tính tổng thời gian foreground từ 00:00 hôm nay tới hiện tại bằng cách ghép các sự kiện
     * ACTIVITY_RESUMED/PAUSED. Chính xác hơn queryUsageStats (vốn chia theo "bucket" và có thể lệch).
     */
    fun readToday(context: Context, nowMillis: Long = System.currentTimeMillis()): Snapshot {
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = manager.queryEvents(startOfDay, nowMillis)
        val event = UsageEvents.Event()

        val totals = mutableMapOf<TrackedApp, Long>()
        // package -> thời điểm bắt đầu một đoạn foreground đang mở
        val openSince = mutableMapOf<String, Long>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (pkg !in TrackedApp.allPackages) continue
            val app = TrackedApp.entries.first { pkg in it.packages }
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> openSince[pkg] = event.timeStamp
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = openSince.remove(pkg)
                    if (start != null && event.timeStamp > start) {
                        totals[app] = (totals[app] ?: 0L) + (event.timeStamp - start)
                    }
                }
            }
        }

        var foreground: TrackedApp? = null
        // Đoạn còn mở nghĩa là app đang chạy trên màn hình: cộng tới hiện tại.
        openSince.forEach { (pkg, start) ->
            val app = TrackedApp.entries.first { pkg in it.packages }
            totals[app] = (totals[app] ?: 0L) + (nowMillis - start).coerceAtLeast(0L)
            foreground = app
        }

        return Snapshot(
            minutesToday = TrackedApp.entries.associateWith { (totals[it] ?: 0L) / 60_000L },
            foregroundNow = foreground
        )
    }
}

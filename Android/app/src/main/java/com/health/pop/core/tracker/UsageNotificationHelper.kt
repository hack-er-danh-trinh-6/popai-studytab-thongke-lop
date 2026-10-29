package com.health.pop.core.tracker

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.health.pop.MainActivity

object UsageNotificationHelper {

    const val ALERT_CHANNEL_ID = "pop_usage_limit"
    const val MONITOR_CHANNEL_ID = "pop_usage_monitor"
    const val MONITOR_NOTIFICATION_ID = 7001
    const val EXTRA_OPEN_TRACKER = "com.health.pop.extra.OPEN_TRACKER"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(ALERT_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(ALERT_CHANNEL_ID, "Cảnh báo thời gian dùng app", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Nhắc nghỉ ngơi khi dùng TikTok, YouTube quá hạn mức bạn đã đặt"
                }
            )
        }
        if (manager.getNotificationChannel(MONITOR_CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(MONITOR_CHANNEL_ID, "Đang theo dõi thời gian dùng app", NotificationManager.IMPORTANCE_MIN).apply {
                    description = "Thông báo nhỏ giúp POP AI chạy nền để theo dõi thời gian sử dụng"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun openTrackerIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        1,
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_TRACKER, true)
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Thông báo cố định (bắt buộc) của dịch vụ nền theo dõi. */
    fun buildMonitorNotification(context: Context): Notification {
        ensureChannels(context)
        return NotificationCompat.Builder(context, MONITOR_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("POP AI đang theo dõi thời gian dùng app")
            .setContentText("Bạn sẽ được nhắc khi vượt hạn mức đã đặt")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openTrackerIntent(context))
            .build()
    }

    @SuppressLint("MissingPermission") // đã kiểm tra areNotificationsEnabled() (gồm POST_NOTIFICATIONS)
    fun showLimitNotification(
        context: Context,
        app: TrackedApp,
        usedMinutes: Long,
        limitMinutes: Int,
        stage: Int
    ) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        ensureChannels(context)

        val (title, body) = if (stage == UsageRules.STAGE_OVER) {
            "Bạn đã vượt hạn dùng ${app.displayName}" to
                "Hôm nay bạn đã xem ${app.displayName} ${formatMinutes(usedMinutes)} (hạn mức ${formatMinutes(limitMinutes.toLong())}). Hãy nghỉ ngơi, rời mắt khỏi màn hình một lát nhé."
        } else {
            "Sắp hết thời gian ${app.displayName}" to
                "Bạn đã dùng ${formatMinutes(usedMinutes)} trên hạn mức ${formatMinutes(limitMinutes.toLong())}. Chuẩn bị dừng lại nhé."
        }

        val notification = NotificationCompat.Builder(context, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(openTrackerIntent(context))
            .setAutoCancel(true)
            .build()
        // Mỗi app một ID riêng: thông báo mới thay thế thông báo cũ của chính app đó.
        manager.notify(ALERT_ID_BASE + app.ordinal, notification)
    }

    fun formatMinutes(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> "$h giờ $m phút"
            h > 0 -> "$h giờ"
            else -> "$m phút"
        }
    }

    private const val ALERT_ID_BASE = 7100
}

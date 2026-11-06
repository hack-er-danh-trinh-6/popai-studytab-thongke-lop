package com.health.pop.core.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.health.pop.MainActivity
import com.health.pop.domain.model.ClassNotification

object ClassNotificationHelper {

    const val CHANNEL_ID = "pop_class_announcements"
    const val EXTRA_OPEN_CLASSROOM = "com.health.pop.extra.OPEN_CLASSROOM"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Thông báo từ giáo viên", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Thông báo giáo viên gửi cho lớp của bạn"
            }
        )
    }

    /** Nhấn thông báo có liên kết (vd. bản cập nhật mới) thì mở trình duyệt tới App Hub. */
    private fun openLink(context: Context, item: ClassNotification, url: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun canNotify(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()

    @SuppressLint("MissingPermission") // đã kiểm tra areNotificationsEnabled() (bao gồm quyền POST_NOTIFICATIONS) trước khi gọi
    fun show(context: Context, notifications: List<ClassNotification>) {
        if (notifications.isEmpty() || !canNotify(context)) return
        ensureChannel(context)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_OPEN_CLASSROOM, true)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val manager = NotificationManagerCompat.from(context)
        notifications.forEach { item ->
            val subtitle = listOf(item.senderName, item.className).filter { it.isNotBlank() }.joinToString(" · ")
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle(item.title)
                .setContentText(item.body)
                .setSubText(subtitle.ifBlank { null })
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(item.linkUrl?.let { openLink(context, item, it) } ?: openApp)
                .setAutoCancel(true)
                .build()
            manager.notify(item.id.hashCode(), notification)
        }
    }
}

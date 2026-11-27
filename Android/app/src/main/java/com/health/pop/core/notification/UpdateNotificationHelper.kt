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

/** Thông báo cục bộ khi phát hiện bản cập nhật mới của ứng dụng. */
object UpdateNotificationHelper {

    const val CHANNEL_ID = "pop_app_update"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Cập nhật ứng dụng", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Thông báo khi có phiên bản mới của POP AI"
            }
        )
    }

    @SuppressLint("MissingPermission")
    fun showUpdateAvailable(context: Context, version: String?, apkUrl: String?) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel(context)

        val contentIntent = apkUrl?.takeIf { it.isNotBlank() }?.let {
            PendingIntent.getActivity(
                context,
                NOTIFICATION_ID,
                Intent(Intent.ACTION_VIEW, Uri.parse(it)).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
        } ?: PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("Có bản cập nhật POP AI mới")
            .setContentText(version?.let { "Phiên bản $it đã sẵn sàng để tải về" } ?: "Có phiên bản mới sẵn sàng")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "Phiên bản mới của POP AI đã sẵn sàng. Nhấn vào đây để tải về và cập nhật ứng dụng."
                )
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}

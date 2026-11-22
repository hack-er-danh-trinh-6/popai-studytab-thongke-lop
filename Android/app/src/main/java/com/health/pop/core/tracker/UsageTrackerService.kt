package com.health.pop.core.tracker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Dịch vụ nền kiểm tra thời gian dùng app mỗi 20 giây để thông báo gần như ngay khi chạm hạn mức.
 * (WorkManager chỉ chạy tối thiểu 15 phút/lần nên không đủ nhanh; [UsageTrackerWorker] chỉ là phương án dự phòng.)
 */
class UsageTrackerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = UsageNotificationHelper.buildMonitorNotification(this)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    UsageNotificationHelper.MONITOR_NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(UsageNotificationHelper.MONITOR_NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // Hệ thống không cho chạy nền lúc này (ví dụ khởi động từ nền): dừng, WorkManager sẽ kiểm tra dự phòng.
            stopSelf()
            return START_NOT_STICKY
        }

        if (loop?.isActive != true) {
            loop = scope.launch {
                val store = UsageLimitStore(applicationContext)
                while (isActive) {
                    if (!store.trackingEnabled || !UsageStatsReader.hasUsageAccess(applicationContext)) {
                        stopSelf()
                        break
                    }
                    val snapshot = UsageTracker.check(applicationContext)
                    if (snapshot != null) {
                        try { UsageUploader.maybeUpload(applicationContext, snapshot) } catch (_: Exception) { /* thử lại ở lần sau */ }
                    }
                    delay(CHECK_INTERVAL_MS)
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHECK_INTERVAL_MS = 20_000L

        /** Bật dịch vụ nếu tracker đang bật và đã có quyền; gọi khi app đang mở hoặc khi máy khởi động. */
        fun startIfNeeded(context: Context) {
            val app = context.applicationContext
            val store = UsageLimitStore(app)
            if (!store.trackingEnabled || !UsageStatsReader.hasUsageAccess(app)) return
            try {
                ContextCompat.startForegroundService(app, Intent(app, UsageTrackerService::class.java))
            } catch (_: Exception) {
                // Không được phép khởi động dịch vụ nền lúc này; WorkManager sẽ lo phần dự phòng.
            }
        }

        fun stop(context: Context) {
            context.applicationContext.stopService(Intent(context.applicationContext, UsageTrackerService::class.java))
        }
    }
}

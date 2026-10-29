package com.health.pop.core.tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Khởi động lại việc theo dõi sau khi máy bật lại hoặc ứng dụng được cập nhật. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                UsageTrackerWorker.schedule(context)
                UsageTrackerService.startIfNeeded(context)
            }
        }
    }
}

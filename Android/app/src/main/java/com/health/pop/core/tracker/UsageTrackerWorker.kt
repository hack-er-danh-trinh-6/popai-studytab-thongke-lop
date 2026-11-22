package com.health.pop.core.tracker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/** Dự phòng: kiểm tra mỗi ~15 phút phòng khi hệ thống tắt dịch vụ nền. */
class UsageTrackerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val snapshot = UsageTracker.check(applicationContext)
        if (snapshot != null) {
            try { UsageUploader.maybeUpload(applicationContext, snapshot) } catch (_: Exception) { /* thử lại ở lần sau */ }
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "pop_usage_tracker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageTrackerWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context.applicationContext).cancelUniqueWork(UNIQUE_NAME)
        }
    }
}

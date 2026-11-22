package com.health.pop.core.tracker

import android.content.Context
import com.health.pop.core.network.ApiResult
import com.health.pop.core.security.TokenManager
import com.health.pop.data.remote.UsageEntryDto
import com.health.pop.data.repository.ClassroomRepository
import java.time.LocalDate

/**
 * Gửi số phút dùng TikTok/YouTube hôm nay lên máy chủ (chỉ tài khoản học sinh, chỉ khi đang bật theo dõi).
 * Chỉ gửi khi số phút thay đổi và cách lần gửi trước ít nhất [MIN_INTERVAL_MS] để tiết kiệm pin và mạng.
 */
object UsageUploader {

    private const val PREFS = "pop_usage_upload"
    private const val MIN_INTERVAL_MS = 5 * 60_000L

    suspend fun maybeUpload(
        context: Context,
        snapshot: UsageStatsReader.Snapshot,
        nowMillis: Long = System.currentTimeMillis(),
        day: LocalDate = LocalDate.now()
    ) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        val fingerprint = TrackedApp.entries.joinToString("|") { "${it.key}=${snapshot.minutesToday[it] ?: 0L}" }
        val lastKey = prefs.getString("last_key", null)
        val lastAt = prefs.getLong("last_at", 0L)
        val key = "$day;$fingerprint"
        if (key == lastKey) return
        // Ngày mới thì gửi ngay; còn lại chờ đủ khoảng cách giữa hai lần gửi.
        val sameDay = lastKey?.startsWith("$day;") == true
        if (sameDay && nowMillis - lastAt < MIN_INTERVAL_MS) return
        // Chưa dùng gì và chưa từng gửi trong ngày thì không cần gửi bản ghi 0 phút.
        if (!sameDay && TrackedApp.entries.all { (snapshot.minutesToday[it] ?: 0L) == 0L }) return

        val tokenManager = try {
            TokenManager(app)
        } catch (_: Exception) {
            return
        }
        if (tokenManager.getAccessToken().isNullOrBlank() || tokenManager.getUser()?.role != "STUDENT") return

        val entries = TrackedApp.entries.map {
            UsageEntryDto(
                date = day.toString(),
                app = it.key,
                minutes = (snapshot.minutesToday[it] ?: 0L).coerceIn(0L, 1440L).toInt()
            )
        }
        val result = ClassroomRepository(tokenManager).uploadUsage(entries)
        if (result is ApiResult.Success) {
            prefs.edit().putString("last_key", key).putLong("last_at", nowMillis).apply()
        }
    }
}

package com.health.pop.data.repository

import com.health.pop.core.network.ApiResult
import com.health.pop.core.network.RetrofitClient
import com.health.pop.core.security.TokenManager
import com.health.pop.data.remote.FocusSessionRequestDto
import com.health.pop.data.remote.PopApiService
import com.health.pop.data.remote.apiErrorMessage
import com.health.pop.domain.model.FocusSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Streak tập trung của người dùng: số ngày liên tiếp và hôm nay đã đạt chưa. */
data class FocusStreak(val days: Int, val earnedToday: Boolean)

class FocusRepository(private val tokenManager: TokenManager) {

    private val apiService: PopApiService by lazy {
        RetrofitClient.getClient(tokenManager).create(PopApiService::class.java)
    }
    private val _currentSession = MutableStateFlow(FocusSession())
    val currentSession: StateFlow<FocusSession> = _currentSession.asStateFlow()

    fun updateSession(session: FocusSession) {
        _currentSession.value = session
    }

    /** Xoá phiên đang giữ trong bộ nhớ (khi đăng xuất) để không còn trạng thái "đang tập trung" của tài khoản cũ. */
    fun reset() {
        _currentSession.value = FocusSession()
    }

    suspend fun syncSessionToServer(session: FocusSession): ApiResult<Unit> {
        return try {
            val dto = FocusSessionRequestDto(
                clientSessionId = session.id,
                taskName = session.taskName,
                targetMinutes = session.targetMinutes,
                actualMinutes = session.actualMinutes,
                interruptionCount = session.interruptionCount,
                interruptionReasons = session.interruptionReasons,
                selfRating = session.selfRating,
                status = session.state.name
            )
            val response = apiService.submitFocusSession(dto)
            if (response.isSuccessful) {
                markSyncStateIfCurrent(session, synced = true)
                ApiResult.Success(Unit)
            } else {
                markSyncStateIfCurrent(session, synced = false)
                ApiResult.Error(response.apiErrorMessage("Phiên chưa được đồng bộ với máy chủ."), response.code())
            }
        } catch (_: Exception) {
            markSyncStateIfCurrent(session, synced = false)
            ApiResult.Error("Không thể kết nối máy chủ. Phiên tập trung chưa được đồng bộ.")
        }
    }

    /** Lấy streak tập trung hiện tại (số ngày liên tiếp có phiên >= 60 phút) từ máy chủ. */
    suspend fun fetchFocusStreak(): ApiResult<FocusStreak> {
        return try {
            val endDate = SimpleDateFormat("dd/MM/yyyy", Locale.US).format(Date())
            val response = apiService.getProgressSummary(1, endDate, TimeZone.getDefault().id)
            val dto = response.body()
            if (response.isSuccessful && dto != null) {
                ApiResult.Success(FocusStreak(dto.focusStreakDays, dto.focusStreakEarnedToday))
            } else {
                ApiResult.Error(response.apiErrorMessage("Không thể tải streak tập trung."), response.code())
            }
        } catch (_: Exception) {
            ApiResult.Error("Không thể kết nối máy chủ để tải streak tập trung.")
        }
    }

    private fun markSyncStateIfCurrent(session: FocusSession, synced: Boolean) {
        val current = _currentSession.value
        if (current.id == session.id && current.copy(isSynced = false) == session.copy(isSynced = false)) {
            _currentSession.value = current.copy(isSynced = synced)
        }
    }
}

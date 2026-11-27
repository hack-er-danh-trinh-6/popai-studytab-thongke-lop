package com.health.pop.data.repository

import com.health.pop.core.network.ApiResult
import com.health.pop.core.network.RetrofitClient
import com.health.pop.core.security.TokenManager
import com.health.pop.data.remote.AppUpdateApiService
import com.health.pop.data.remote.AppUpdateCheckDto
import com.health.pop.data.remote.apiErrorMessage
import retrofit2.Response

class AppUpdateRepository(private val tokenManager: TokenManager) {

    private val api: AppUpdateApiService by lazy {
        RetrofitClient.getClient(tokenManager).create(AppUpdateApiService::class.java)
    }

    private val networkMessage = "Không thể kiểm tra bản cập nhật. Vui lòng thử lại sau."

    /** Kiểm tra phiên bản mới nhất từ máy chủ. Không yêu cầu đăng nhập. */
    suspend fun checkLatestVersion(): ApiResult<AppUpdateCheckDto> = call("Không thể kiểm tra bản cập nhật.") {
        val response = api.checkLatestVersion()
        if (response.isSuccessful) {
            ApiResult.Success(response.body() ?: AppUpdateCheckDto())
        } else {
            ApiResult.Error(response.apiErrorMessage(networkMessage), response.code())
        }
    }

    private suspend fun <T> call(defaultError: String, block: suspend () -> ApiResult<T>): ApiResult<T> {
        return try {
            block()
        } catch (_: Exception) {
            ApiResult.Error(networkMessage)
        }
    }
}

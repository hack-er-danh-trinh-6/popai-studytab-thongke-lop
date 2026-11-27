package com.health.pop.data.remote

import retrofit2.Response
import retrofit2.http.GET

interface AppUpdateApiService {

    @GET("api/mobile/v1/update/latest")
    suspend fun checkLatestVersion(): Response<AppUpdateCheckDto>
}

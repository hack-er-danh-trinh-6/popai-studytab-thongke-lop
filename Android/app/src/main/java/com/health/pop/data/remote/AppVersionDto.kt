package com.health.pop.data.remote

// Thông tin phiên bản ứng dụng trả từ GET /api/mobile/v1/update/latest.
// Gson có thể gán null cho trường bị thiếu, nên các trường đều nullable.
data class AppVersionDto(
    val version: String? = null,
    val versionCode: Int? = null,
    val apkUrl: String? = null,
    val apkFileName: String? = null,
    val apkSize: Long? = null,
    val createdAt: String? = null
)

data class AppUpdateCheckDto(
    val latest: AppVersionDto? = null
)

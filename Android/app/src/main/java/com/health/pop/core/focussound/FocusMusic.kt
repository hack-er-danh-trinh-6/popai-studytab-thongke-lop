package com.health.pop.core.focussound

import com.health.pop.BuildConfig

/**
 * Nhạc tập trung được stream từ backend (không đóng gói trong APK để app nhẹ).
 * File nằm ở `Backend/media/focus-music.mp3`, phục vụ qua `GET /media/focus-music.mp3`.
 */
object FocusMusic {
    const val PATH = "media/focus-music.mp3"

    /** Âm lượng phát nhạc: 30% (thang tuyến tính của MediaPlayer). */
    const val VOLUME = 0.30f

    /** Âm lượng tạm thời khi app khác cần "duck" (ví dụ thông báo bằng giọng nói). */
    const val DUCKED_VOLUME = 0.10f

    fun url(baseUrl: String = BuildConfig.BASE_URL): String = baseUrl.trimEnd('/') + "/" + PATH
}

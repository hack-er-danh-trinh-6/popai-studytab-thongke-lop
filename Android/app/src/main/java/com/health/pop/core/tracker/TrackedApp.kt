package com.health.pop.core.tracker

/** Các ứng dụng được theo dõi thời gian sử dụng. Một app có thể có nhiều package (ví dụ TikTok theo từng khu vực). */
enum class TrackedApp(
    val key: String,
    val displayName: String,
    val emoji: String,
    val packages: List<String>
) {
    TIKTOK(
        key = "tiktok",
        displayName = "TikTok",
        emoji = "🎵",
        packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
    ),
    YOUTUBE(
        key = "youtube",
        displayName = "YouTube",
        emoji = "▶️",
        packages = listOf("com.google.android.youtube")
    );

    companion object {
        fun fromKey(key: String): TrackedApp? = entries.firstOrNull { it.key == key }
        val allPackages: Set<String> = entries.flatMap { it.packages }.toSet()
    }
}

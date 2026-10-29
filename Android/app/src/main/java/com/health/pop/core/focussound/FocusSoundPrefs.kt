package com.health.pop.core.focussound

import android.content.Context

/** Lưu lựa chọn bật/tắt nhạc tập trung của người dùng. */
class FocusSoundPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pop_focus_sound", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", true)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()
}

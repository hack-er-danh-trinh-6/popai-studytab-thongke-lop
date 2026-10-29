package com.health.pop.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.health.pop.domain.model.ChatMessage
import com.health.pop.domain.usecase.ChatHistoryRules

/**
 * Nơi lưu lịch sử trò chuyện với POP-AI trong bộ nhớ app. Các hàm đọc/ghi là hàm chặn:
 * gọi từ luồng nền (AIViewModel dùng Dispatchers.IO).
 */
interface ChatHistoryStore {
    fun load(): List<ChatMessage>
    fun save(messages: List<ChatMessage>)
    fun clear()
}

/** Chỉ giữ trong RAM (mất khi đóng app): dùng khi không có tài khoản hoặc bộ nhớ an toàn không khởi tạo được. */
class InMemoryChatHistoryStore : ChatHistoryStore {
    @Volatile
    private var messages: List<ChatMessage> = emptyList()

    override fun load(): List<ChatMessage> = messages
    override fun save(messages: List<ChatMessage>) {
        this.messages = messages
    }

    override fun clear() {
        messages = emptyList()
    }
}

/**
 * Lưu lịch sử đã mã hoá (EncryptedSharedPreferences, cùng cách với TokenManager) vì có thể chứa nội dung riêng tư
 * như phản tư nhật ký. Mỗi tài khoản một khoá riêng nên người dùng khác trên cùng máy không đọc được; đăng xuất không xoá,
 * đăng nhập lại cùng tài khoản vẫn thấy lịch sử.
 *
 * Nếu không khởi tạo được bộ nhớ mã hoá (lỗi Keystore hiếm gặp trên một số máy), app vẫn chạy bình thường nhưng
 * lịch sử không được lưu.
 */
class EncryptedChatHistoryStore(
    context: Context,
    userId: String
) : ChatHistoryStore {

    private val appContext = context.applicationContext
    private val key = "history_$userId"

    private val prefs: SharedPreferences? by lazy { openPrefs() }

    override fun load(): List<ChatMessage> = ChatHistoryCodec.decode(prefs?.getString(key, null))

    override fun save(messages: List<ChatMessage>) {
        val trimmed = ChatHistoryRules.trim(messages)
        prefs?.edit()?.putString(key, ChatHistoryCodec.encode(trimmed))?.apply()
    }

    override fun clear() {
        prefs?.edit()?.remove(key)?.apply()
    }

    private fun openPrefs(): SharedPreferences? = try {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            FILE_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val FILE_NAME = "pop_secure_ai_chat"
    }
}

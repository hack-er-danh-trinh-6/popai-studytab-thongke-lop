package com.health.pop.data.local

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.health.pop.domain.model.ChatMessage
import com.health.pop.domain.model.ChatRole

/**
 * Chuyển lịch sử trò chuyện <-> JSON để lưu trong bộ nhớ app.
 *
 * Gson có thể gán null cho trường bị thiếu dù kiểu Kotlin là non-null, nên dữ liệu được đọc vào các lớp
 * "Stored*" toàn nullable rồi kiểm tra từng trường. JSON hỏng hoặc sai cấu trúc không làm app lỗi: trả về danh sách rỗng,
 * tin nhắn không hợp lệ bị bỏ qua.
 */
internal object ChatHistoryCodec {
    private const val VERSION = 1
    private const val MAX_TEXT_LENGTH = 8_000
    private const val MAX_STEPS = 10

    private val gson = Gson()

    private data class StoredMessage(
        @SerializedName("id") val id: String? = null,
        @SerializedName("role") val role: String? = null,
        @SerializedName("text") val text: String? = null,
        @SerializedName("createdAt") val createdAt: Long? = null,
        @SerializedName("steps") val steps: List<String?>? = null,
        @SerializedName("addedSteps") val addedSteps: List<String?>? = null,
        @SerializedName("withReflection") val withReflection: Boolean? = null,
        @SerializedName("reflectionMissing") val reflectionMissing: Boolean? = null,
        @SerializedName("isError") val isError: Boolean? = null
    )

    private data class StoredHistory(
        @SerializedName("version") val version: Int = VERSION,
        @SerializedName("messages") val messages: List<StoredMessage?>? = null
    )

    fun encode(messages: List<ChatMessage>): String =
        gson.toJson(StoredHistory(VERSION, messages.map { it.toStored() }))

    fun decode(json: String?): List<ChatMessage> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            gson.fromJson(json, StoredHistory::class.java)
                ?.messages
                .orEmpty()
                .mapNotNull { it?.toDomain() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun ChatMessage.toStored() = StoredMessage(
        id = id,
        role = role.name,
        text = text,
        createdAt = createdAt,
        steps = steps,
        addedSteps = addedSteps,
        withReflection = withReflection,
        reflectionMissing = reflectionMissing,
        isError = isError
    )

    private fun StoredMessage.toDomain(): ChatMessage? {
        val parsedRole = when (role) {
            ChatRole.USER.name -> ChatRole.USER
            ChatRole.ASSISTANT.name -> ChatRole.ASSISTANT
            else -> return null
        }
        val parsedId = id?.takeIf { it.isNotBlank() } ?: return null
        val parsedTime = createdAt?.takeIf { it > 0L } ?: return null
        val parsedText = (text ?: "").take(MAX_TEXT_LENGTH)
        val parsedSteps = cleanList(steps)
        if (parsedText.isBlank() && parsedSteps.isEmpty()) return null
        return ChatMessage(
            id = parsedId,
            role = parsedRole,
            text = parsedText,
            createdAt = parsedTime,
            steps = parsedSteps,
            addedSteps = cleanList(addedSteps).filter { it in parsedSteps },
            withReflection = withReflection ?: false,
            reflectionMissing = reflectionMissing ?: false,
            isError = isError ?: false
        )
    }

    private fun cleanList(values: List<String?>?): List<String> =
        values.orEmpty()
            .mapNotNull { it?.trim()?.takeIf { value -> value.isNotEmpty() } }
            .take(MAX_STEPS)
}

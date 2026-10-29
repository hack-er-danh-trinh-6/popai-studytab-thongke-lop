package com.health.pop.domain.model

enum class ChatRole { USER, ASSISTANT }

/** Một tin nhắn trong cuộc trò chuyện với POP-AI (được lưu trong bộ nhớ app). */
data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    /** Thời điểm tạo, tính bằng mili-giây epoch. */
    val createdAt: Long,
    /** Các hành động POP-AI gợi ý (chỉ có ở tin nhắn của POP-AI). */
    val steps: List<String> = emptyList(),
    /** Các hành động trong [steps] đã được thêm vào kế hoạch cá nhân. */
    val addedSteps: List<String> = emptyList(),
    /** Tin nhắn của người dùng: có đính kèm nhật ký hôm nay hay không. */
    val withReflection: Boolean = false,
    /** Tin nhắn của POP-AI: người dùng đã bật đính kèm nhật ký nhưng hôm nay chưa có nhật ký. */
    val reflectionMissing: Boolean = false,
    /** Tin báo lỗi của POP-AI; không dùng làm ngữ cảnh cho các câu hỏi sau. */
    val isError: Boolean = false
)

/** Một lượt hội thoại gửi kèm để POP-AI hiểu câu hỏi nối tiếp. */
data class ChatTurn(
    val role: ChatRole,
    val text: String
)

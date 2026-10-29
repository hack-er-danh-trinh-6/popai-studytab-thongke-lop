package com.health.pop.domain.usecase

import com.health.pop.domain.model.ChatMessage
import com.health.pop.domain.model.ChatRole
import com.health.pop.domain.model.ChatTurn

/** Quy tắc thuần (không phụ thuộc Android) cho lịch sử trò chuyện với POP-AI. */
object ChatHistoryRules {

    /** Số tin nhắn tối đa giữ lại trên máy. */
    const val MAX_MESSAGES = 150

    /** Tổng số ký tự tối đa của lịch sử, để file lưu trữ không phình to. */
    const val MAX_TOTAL_CHARS = 120_000

    /** Số lượt hội thoại gần nhất gửi kèm mỗi câu hỏi (server cũng giới hạn lại). */
    const val MAX_CONTEXT_TURNS = 6
    const val MAX_CONTEXT_CHARS = 600

    const val INTERRUPTED_MESSAGE =
        "Phiên trước bị gián đoạn nên câu hỏi này chưa được trả lời. Bạn thử gửi lại nhé."

    /**
     * Giữ các tin nhắn mới nhất trong giới hạn [maxMessages] / [maxChars]. Luôn giữ ít nhất tin mới nhất,
     * và bỏ các tin của POP-AI đứng đầu danh sách mà câu hỏi tương ứng đã bị cắt.
     */
    fun trim(
        messages: List<ChatMessage>,
        maxMessages: Int = MAX_MESSAGES,
        maxChars: Int = MAX_TOTAL_CHARS
    ): List<ChatMessage> {
        if (messages.isEmpty()) return messages
        var count = 0
        var total = 0
        var start = messages.size
        for (index in messages.indices.reversed()) {
            val cost = messages[index].text.length + messages[index].steps.sumOf { it.length }
            if (count >= maxMessages || (count > 0 && total + cost > maxChars)) break
            count++
            total += cost
            start = index
        }
        val kept = messages.subList(start, messages.size)
        val firstUser = kept.indexOfFirst { it.role == ChatRole.USER }
        return if (firstUser > 0) kept.subList(firstUser, kept.size).toList() else kept.toList()
    }

    /**
     * Các lượt hội thoại đứng trước tin nhắn người dùng [userMessageId], dùng làm ngữ cảnh cho POP-AI.
     * Bỏ tin báo lỗi và tin rỗng, mỗi lượt cắt còn [MAX_CONTEXT_CHARS] ký tự, chỉ lấy [maxTurns] lượt gần nhất.
     */
    fun contextBefore(
        messages: List<ChatMessage>,
        userMessageId: String,
        maxTurns: Int = MAX_CONTEXT_TURNS
    ): List<ChatTurn> {
        val index = messages.indexOfFirst { it.id == userMessageId }
        if (index <= 0) return emptyList()
        return messages.subList(0, index)
            .filter { !it.isError }
            .mapNotNull { message ->
                val content = message.text.ifBlank { message.steps.joinToString("; ") }
                    .trim()
                    .take(MAX_CONTEXT_CHARS)
                if (content.isEmpty()) null else ChatTurn(message.role, content)
            }
            .takeLast(maxTurns)
    }

    /**
     * Nếu app bị đóng khi đang chờ POP-AI, tin cuối cùng sẽ là câu hỏi chưa có câu trả lời:
     * thêm một tin báo lỗi (có nút "Thử lại") thay vì để câu hỏi lơ lửng.
     */
    fun recoverInterrupted(messages: List<ChatMessage>, now: Long): List<ChatMessage> {
        val last = messages.lastOrNull() ?: return messages
        if (last.role != ChatRole.USER) return messages
        return messages + ChatMessage(
            id = "interrupted_${last.id}",
            role = ChatRole.ASSISTANT,
            text = INTERRUPTED_MESSAGE,
            createdAt = maxOf(now, last.createdAt),
            isError = true
        )
    }

    /** Tin nhắn người dùng gần nhất đứng trước tin báo lỗi [errorId]; null nếu không tìm thấy. */
    fun userMessageBefore(messages: List<ChatMessage>, errorId: String): ChatMessage? {
        val index = messages.indexOfFirst { it.id == errorId }
        if (index <= 0) return null
        return messages.subList(0, index).lastOrNull { it.role == ChatRole.USER }
    }
}

package com.health.pop.domain.usecase

import com.health.pop.domain.model.ChatMessage
import com.health.pop.domain.model.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryRulesTest {

    private fun user(id: String, text: String = "hỏi $id", at: Long = 1_000L) =
        ChatMessage(id = id, role = ChatRole.USER, text = text, createdAt = at)

    private fun ai(id: String, text: String = "đáp $id", at: Long = 2_000L, error: Boolean = false, steps: List<String> = emptyList()) =
        ChatMessage(id = id, role = ChatRole.ASSISTANT, text = text, createdAt = at, isError = error, steps = steps)

    @Test
    fun trim_keepsEverythingWhenWithinLimits() {
        val list = listOf(user("u1"), ai("a1"), user("u2"), ai("a2"))
        assertEquals(list, ChatHistoryRules.trim(list))
    }

    @Test
    fun trim_dropsOldestMessagesBeyondMaxCount() {
        val list = (1..10).flatMap { listOf(user("u$it"), ai("a$it")) }
        val trimmed = ChatHistoryRules.trim(list, maxMessages = 6)
        assertEquals(listOf("u8", "a8", "u9", "a9", "u10", "a10"), trimmed.map { it.id })
    }

    @Test
    fun trim_dropsLeadingAssistantMessageWhoseQuestionWasCut() {
        val list = listOf(user("u1"), ai("a1"), user("u2"), ai("a2"))
        // Giới hạn 3 tin sẽ giữ a1, u2, a2: a1 mồ côi nên bị bỏ.
        val trimmed = ChatHistoryRules.trim(list, maxMessages = 3)
        assertEquals(listOf("u2", "a2"), trimmed.map { it.id })
    }

    @Test
    fun trim_respectsTotalCharacterBudgetButKeepsNewestMessage() {
        val list = listOf(user("u1", "x".repeat(50)), ai("a1", "y".repeat(50)), user("u2", "z".repeat(50)))
        val trimmed = ChatHistoryRules.trim(list, maxChars = 60)
        assertEquals(listOf("u2"), trimmed.map { it.id })

        val huge = listOf(user("u1", "x".repeat(500)))
        assertEquals(huge, ChatHistoryRules.trim(huge, maxChars = 10))
    }

    @Test
    fun trim_emptyListStaysEmpty() {
        assertTrue(ChatHistoryRules.trim(emptyList()).isEmpty())
    }

    @Test
    fun contextBefore_returnsOnlyTurnsBeforeTheQuestion() {
        val list = listOf(user("u1"), ai("a1"), user("u2"), ai("a2"), user("u3"))
        val context = ChatHistoryRules.contextBefore(list, "u3")
        assertEquals(listOf("hỏi u1", "đáp a1", "hỏi u2", "đáp a2"), context.map { it.text })
        assertEquals(listOf(ChatRole.USER, ChatRole.ASSISTANT, ChatRole.USER, ChatRole.ASSISTANT), context.map { it.role })
        assertTrue(ChatHistoryRules.contextBefore(list, "u1").isEmpty())
        assertTrue(ChatHistoryRules.contextBefore(list, "không-có").isEmpty())
    }

    @Test
    fun contextBefore_skipsErrorsAndBlankMessagesAndCapsTurnsAndLength() {
        val list = listOf(
            user("u1"),
            ai("e1", "Lỗi mạng", error = true),
            ai("a0", text = "", steps = listOf("Tắt thông báo", "Đặt hẹn giờ")),
            ai("a1", "q".repeat(1_000)),
            user("u2")
        )
        val context = ChatHistoryRules.contextBefore(list, "u2")
        assertEquals(3, context.size)
        assertEquals("Tắt thông báo; Đặt hẹn giờ", context[1].text)
        assertEquals(ChatHistoryRules.MAX_CONTEXT_CHARS, context[2].text.length)

        val many = (1..10).flatMap { listOf(user("u$it"), ai("a$it")) } + user("last")
        val capped = ChatHistoryRules.contextBefore(many, "last")
        assertEquals(ChatHistoryRules.MAX_CONTEXT_TURNS, capped.size)
        assertEquals("đáp a10", capped.last().text)
    }

    @Test
    fun recoverInterrupted_addsRetryableErrorAfterUnansweredQuestion() {
        val list = listOf(user("u1", at = 5_000L), ai("a1"), user("u2", at = 9_000L))
        val recovered = ChatHistoryRules.recoverInterrupted(list, now = 12_000L)
        assertEquals(4, recovered.size)
        val last = recovered.last()
        assertEquals(ChatRole.ASSISTANT, last.role)
        assertTrue(last.isError)
        assertEquals("interrupted_u2", last.id)
        assertEquals(12_000L, last.createdAt)
        assertEquals("u2", ChatHistoryRules.userMessageBefore(recovered, last.id)?.id)
    }

    @Test
    fun recoverInterrupted_leavesAnsweredOrEmptyHistoryUntouched() {
        val answered = listOf(user("u1"), ai("a1"))
        assertEquals(answered, ChatHistoryRules.recoverInterrupted(answered, now = 9_999L))
        assertTrue(ChatHistoryRules.recoverInterrupted(emptyList(), now = 9_999L).isEmpty())
    }

    @Test
    fun recoverInterrupted_neverTimestampsBeforeTheQuestion() {
        val list = listOf(user("u1", at = 50_000L))
        assertEquals(50_000L, ChatHistoryRules.recoverInterrupted(list, now = 1_000L).last().createdAt)
    }

    @Test
    fun userMessageBefore_findsNearestQuestionOrNull() {
        val list = listOf(user("u1"), ai("a1"), user("u2"), ai("e2", error = true))
        assertEquals("u2", ChatHistoryRules.userMessageBefore(list, "e2")?.id)
        assertNull(ChatHistoryRules.userMessageBefore(list, "không-có"))
        assertNull(ChatHistoryRules.userMessageBefore(list, "u1"))
    }
}

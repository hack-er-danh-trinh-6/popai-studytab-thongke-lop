package com.health.pop.data.local

import com.health.pop.domain.model.ChatMessage
import com.health.pop.domain.model.ChatRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatHistoryCodecTest {

    private val sample = listOf(
        ChatMessage(id = "u1", role = ChatRole.USER, text = "Mình hay lướt Shorts \"khi\" học\nbài", createdAt = 1_700_000_000_000L, withReflection = true),
        ChatMessage(
            id = "a1", role = ChatRole.ASSISTANT, text = "Hãy đặt điện thoại ở phòng khác.", createdAt = 1_700_000_005_000L,
            steps = listOf("Tắt thông báo", "Đặt hẹn giờ 25 phút"), addedSteps = listOf("Tắt thông báo"), reflectionMissing = true
        ),
        ChatMessage(id = "e1", role = ChatRole.ASSISTANT, text = "Không thể kết nối mạng.", createdAt = 1_700_000_009_000L, isError = true)
    )

    @Test
    fun roundTrip_preservesEveryField() {
        assertEquals(sample, ChatHistoryCodec.decode(ChatHistoryCodec.encode(sample)))
    }

    @Test
    fun decode_nullBlankOrCorruptJson_returnsEmptyList() {
        assertTrue(ChatHistoryCodec.decode(null).isEmpty())
        assertTrue(ChatHistoryCodec.decode("").isEmpty())
        assertTrue(ChatHistoryCodec.decode("   ").isEmpty())
        assertTrue(ChatHistoryCodec.decode("{không phải json").isEmpty())
        assertTrue(ChatHistoryCodec.decode("[1,2,3]").isEmpty())
        assertTrue(ChatHistoryCodec.decode("{\"messages\":\"sai kiểu\"}").isEmpty())
    }

    @Test
    fun decode_skipsInvalidMessagesButKeepsValidOnes() {
        val json = """
            {"version":1,"messages":[
              {"id":"ok","role":"USER","text":"xin chào","createdAt":1000},
              {"id":"","role":"USER","text":"thiếu id","createdAt":1000},
              {"id":"x1","role":"ADMIN","text":"sai vai trò","createdAt":1000},
              {"id":"x2","role":"USER","text":"thiếu thời gian"},
              {"id":"x3","role":"USER","text":"thời gian âm","createdAt":-5},
              {"id":"x4","role":"ASSISTANT","text":"   ","createdAt":1000},
              null,
              {"id":"ok2","role":"ASSISTANT","text":"","createdAt":2000,"steps":["Một việc nhỏ"]}
            ]}
        """.trimIndent()
        val decoded = ChatHistoryCodec.decode(json)
        assertEquals(listOf("ok", "ok2"), decoded.map { it.id })
        assertEquals(listOf("Một việc nhỏ"), decoded[1].steps)
    }

    @Test
    fun decode_missingOptionalFieldsFallBackToDefaults() {
        val decoded = ChatHistoryCodec.decode("""{"messages":[{"id":"a","role":"USER","text":"hi","createdAt":1}]}""")
        assertEquals(1, decoded.size)
        val message = decoded.first()
        assertTrue(message.steps.isEmpty())
        assertTrue(message.addedSteps.isEmpty())
        assertFalse(message.withReflection)
        assertFalse(message.reflectionMissing)
        assertFalse(message.isError)
    }

    @Test
    fun decode_cleansStepsAndDropsAddedStepsNotInSteps() {
        val json = """
            {"messages":[{"id":"a","role":"ASSISTANT","text":"ok","createdAt":1,
              "steps":["  Tắt thông báo  ","",null,"Đặt hẹn giờ"],
              "addedSteps":["Tắt thông báo","Việc lạ"]}]}
        """.trimIndent()
        val message = ChatHistoryCodec.decode(json).single()
        assertEquals(listOf("Tắt thông báo", "Đặt hẹn giờ"), message.steps)
        assertEquals(listOf("Tắt thông báo"), message.addedSteps)
    }

    @Test
    fun decode_capsVeryLongTextAndStepCount() {
        val longText = "x".repeat(20_000)
        val steps = (1..30).joinToString(",") { "\"việc $it\"" }
        val json = """{"messages":[{"id":"a","role":"ASSISTANT","text":"$longText","createdAt":1,"steps":[$steps]}]}"""
        val message = ChatHistoryCodec.decode(json).single()
        assertEquals(8_000, message.text.length)
        assertEquals(10, message.steps.size)
    }
}

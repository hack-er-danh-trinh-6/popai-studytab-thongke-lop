package com.health.pop.ui.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.health.pop.domain.model.QuickJournal
import com.health.pop.ui.common.PopAzure
import com.health.pop.ui.common.PopButton
import com.health.pop.ui.common.PopErrorBanner
import com.health.pop.ui.common.PopField
import com.health.pop.ui.common.PopMint
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopPink
import com.health.pop.ui.common.PopSun
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.StickerCard

private val DISTRACTOR_OPTIONS = listOf("Mạng xã hội", "Thông báo game", "Tiếng ồn", "Bạn bè rủ rê", "Khác")

/** Nội dung đang nhập của nhật ký hôm nay (giữ ở màn cha để không mất khi chuyển mục). */
@Stable
internal class JournalDraft(journal: QuickJournal) {
    val date: String = journal.date
    var focusMinutes by mutableStateOf(journal.focusMinutes.toString())
    var videoMinutes by mutableStateOf(journal.estimatedShortVideoMinutes.toString())
    var interruptions by mutableStateOf(journal.interruptionCount.toString())
    var productiveTime by mutableStateOf(journal.mostProductiveSession)
    var tomorrow by mutableStateOf(journal.tomorrowAdjustment)
    val distractors = mutableStateListOf(*journal.distractors.toTypedArray())

    /** Thông báo lỗi nếu dữ liệu chưa hợp lệ, null nếu ổn. Giới hạn khớp với máy chủ. */
    fun validate(): String? {
        val video = videoMinutes.toIntOrNull()
        val focus = focusMinutes.toIntOrNull()
        val interrupted = interruptions.toIntOrNull()
        return when {
            focus == null || focus !in 0..1440 -> "Số phút tập trung phải từ 0 đến 1440."
            video == null || video !in 0..1440 -> "Số phút lướt video phải từ 0 đến 1440."
            interrupted == null || interrupted !in 0..10000 -> "Số lần bị ngắt quãng phải từ 0 đến 10000."
            else -> null
        }
    }

    /** Chỉ gọi sau khi [validate] trả về null. */
    fun toJournal(): QuickJournal = QuickJournal(
        date = date,
        estimatedShortVideoMinutes = videoMinutes.toInt(),
        focusMinutes = focusMinutes.toInt(),
        interruptionCount = interruptions.toInt(),
        distractors = distractors.toList(),
        mostProductiveSession = productiveTime,
        tomorrowAdjustment = tomorrow
    )
}

/** Mục "Nhật ký": vài câu hỏi ngắn cuối ngày; hai ô ít quan trọng được thu gọn. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.JournalSection(
    c: PopPalette,
    draft: JournalDraft,
    saving: Boolean,
    saveError: String?,
    formError: String?,
    onSave: () -> Unit
) {
    PopText(
        "Cuối ngày, trả lời vài câu hỏi ngắn (chưa đến 1 phút) để nhìn lại hôm nay." +
            if (draft.date.isNotBlank()) " Ngày ${draft.date}." else "",
        c.textSoft,
        14
    )

    StickerCard(c, contentPadding = 18.dp) {
        SectionHeader("🎯", PopMint, "Hôm nay bạn tập trung được bao nhiêu phút?", c, hint = "Cộng các lần học tập trung trong ngày")
        Spacer(Modifier.height(12.dp))
        PopField(
            value = draft.focusMinutes,
            onValueChange = { draft.focusMinutes = it },
            hint = "Số phút, ví dụ 90",
            c = c,
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Next
        )
    }

    StickerCard(c, contentPadding = 18.dp) {
        SectionHeader("📺", PopPink, "Bạn đã lướt video ngắn khoảng bao nhiêu phút?", c, hint = "Ước chừng là được, không cần chính xác")
        Spacer(Modifier.height(12.dp))
        PopField(
            value = draft.videoMinutes,
            onValueChange = { draft.videoMinutes = it },
            hint = "Số phút, ví dụ 45",
            c = c,
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        )
    }

    StickerCard(c, contentPadding = 18.dp) {
        SectionHeader("📱", PopAzure, "Điều gì làm bạn xao nhãng?", c, hint = "Chạm để chọn, được chọn nhiều ý")
        Spacer(Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DISTRACTOR_OPTIONS.forEach { option ->
                val selected = draft.distractors.contains(option)
                ToggleChip(option, selected, c) {
                    if (selected) draft.distractors.remove(option) else draft.distractors.add(option)
                }
            }
        }
    }

    StickerCard(c, contentPadding = 18.dp) {
        SectionHeader("🌱", PopMint, "Ngày mai bạn muốn thử điều gì?", c, hint = "Một việc nhỏ thôi")
        Spacer(Modifier.height(12.dp))
        PopField(
            value = draft.tomorrow,
            onValueChange = { draft.tomorrow = it },
            hint = "Ví dụ: Để điện thoại im lặng khi học bài",
            c = c,
            singleLine = false,
            minLines = 2,
            imeAction = ImeAction.Default
        )
    }

    MoreOptions(title = "Thêm chi tiết (không bắt buộc)", c = c) {
        StickerCard(c, contentPadding = 18.dp) {
            SectionHeader("🔔", PopSun, "Bạn bị ngắt quãng mấy lần?", c, hint = "Ví dụ: có tin nhắn, có người gọi")
            Spacer(Modifier.height(12.dp))
            PopField(
                value = draft.interruptions,
                onValueChange = { draft.interruptions = it },
                hint = "Số lần, ví dụ 3",
                c = c,
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Next
            )
        }
        StickerCard(c, contentPadding = 18.dp) {
            SectionHeader("⏰", PopAzure, "Lúc nào bạn học tốt nhất?", c)
            Spacer(Modifier.height(12.dp))
            PopField(
                value = draft.productiveTime,
                onValueChange = { draft.productiveTime = it },
                hint = "Ví dụ: 19:30 - 20:00",
                c = c,
                imeAction = ImeAction.Done
            )
        }
    }

    formError?.let { PopErrorBanner(it, c) }
    saveError?.let { PopErrorBanner(it, c) }

    PopButton(text = "Lưu nhật ký hôm nay", c = c, loading = saving, onClick = onSave)
}

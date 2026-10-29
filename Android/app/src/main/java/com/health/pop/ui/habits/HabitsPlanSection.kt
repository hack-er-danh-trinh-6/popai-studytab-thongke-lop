package com.health.pop.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.health.pop.domain.model.PersonalPlan
import com.health.pop.ui.common.PopAzure
import com.health.pop.ui.common.PopButton
import com.health.pop.ui.common.PopCoral
import com.health.pop.ui.common.PopErrorBanner
import com.health.pop.ui.common.PopField
import com.health.pop.ui.common.PopInk
import com.health.pop.ui.common.PopMint
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopPink
import com.health.pop.ui.common.PopSun
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.StickerCard

private val DURATION_OPTIONS = listOf(10, 15, 25, 30, 45)
private val TARGET_DAY_OPTIONS = listOf(7, 30)

/** Nội dung đang chỉnh của kế hoạch. Giữ ở màn cha để không mất khi chuyển qua mục Nhật ký rồi quay lại. */
@Stable
internal class PlanDraft(plan: PersonalPlan) {
    var dailyLimit by mutableStateOf(plan.dailyShortVideoLimitMinutes.toString())
    var protectionHours by mutableStateOf(plan.focusProtectionHours)
    var duration by mutableStateOf(plan.sessionDurationMinutes)
    var targetDays by mutableStateOf(plan.targetDays)
    val tasks = mutableStateListOf(*plan.studyTasks.toTypedArray())
    var newTask by mutableStateOf("")

    fun addNewTask() {
        val text = newTask.trim()
        if (text.isNotEmpty()) {
            tasks.add(text)
            newTask = ""
        }
    }

    /** Thông báo lỗi tiếng Việt nếu dữ liệu chưa hợp lệ, null nếu ổn. Giới hạn khớp với máy chủ. */
    fun validate(): String? {
        val limit = dailyLimit.toIntOrNull()
        return when {
            limit == null || limit !in 0..1440 -> "Số phút lướt video phải từ 0 đến 1440."
            protectionHours.length > 100 -> "Khung giờ tập trung tối đa 100 ký tự."
            tasks.size > 30 || tasks.any { it.trim().length > 200 } ->
                "Tối đa 30 việc, mỗi việc không quá 200 ký tự."
            else -> null
        }
    }
}

/** Mục "Kế hoạch": chỉ cần thêm vài việc cần làm; các cài đặt khác có sẵn giá trị gợi ý. */
@Composable
internal fun ColumnScope.PlanSection(
    c: PopPalette,
    draft: PlanDraft,
    hasSavedPlan: Boolean,
    saving: Boolean,
    saveError: String?,
    formError: String?,
    onSave: () -> Unit
) {
    PopText(
        if (hasSavedPlan) {
            "Các việc bạn lưu ở đây sẽ hiện trong màn Hôm nay. Sửa xong nhớ bấm Lưu."
        } else {
            "Bắt đầu bằng 1–2 việc nhỏ bạn muốn làm hôm nay, rồi bấm Lưu. Các ô còn lại đã có sẵn gợi ý."
        },
        c.textSoft,
        14
    )

    // 1. Việc cần làm: thứ người dùng quan tâm nhất nên đặt trên cùng.
    StickerCard(c, contentPadding = 18.dp) {
        SectionHeader(
            emoji = "📚",
            color = PopAzure,
            title = "Việc cần làm",
            hint = "Ví dụ: Làm bài tập Toán, ôn từ vựng tiếng Anh",
            c = c,
            trailing = {
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(c.accent.copy(alpha = 0.18f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    PopText("${draft.tasks.size} việc", c.accent, 13, weight = FontWeight.ExtraBold, maxLines = 1)
                }
            }
        )
        Spacer(Modifier.height(8.dp))

        draft.tasks.forEachIndexed { index, task ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(22.dp)
                        .border(2.5.dp, c.accent, CircleShape)
                )
                Spacer(Modifier.width(12.dp))
                PopText(task, c.text, 15, Modifier.weight(1f), FontWeight.SemiBold)
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            onClickLabel = "Xóa việc này",
                            role = Role.Button,
                            onClick = { draft.tasks.removeAt(index) }
                        )
                        .semantics { contentDescription = "Xóa" },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(PopCoral.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        PopText("✕", PopCoral, 13, weight = FontWeight.Black)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        PopField(
            value = draft.newTask,
            onValueChange = { draft.newTask = it },
            hint = "Gõ một việc rồi bấm +",
            c = c,
            badge = "✏️",
            badgeColor = PopSun,
            imeAction = ImeAction.Done,
            onImeAction = { draft.addNewTask() },
            clearFocusOnDone = false,
            trailing = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(PopSun)
                        .border(2.dp, c.outline, CircleShape)
                        .clickable(
                            onClickLabel = "Thêm việc",
                            role = Role.Button,
                            onClick = { draft.addNewTask() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    PopText("+", PopInk, 22, weight = FontWeight.Black)
                }
            }
        )
    }

    // 2. Mỗi lần tập trung bao lâu.
    StickerCard(c, contentPadding = 18.dp) {
        SectionHeader(
            emoji = "⏲️",
            color = PopSun,
            title = "Mỗi lần tập trung bao lâu?",
            hint = "25 phút là lựa chọn phổ biến",
            c = c
        )
        Spacer(Modifier.height(12.dp))
        ChoiceRow(DURATION_OPTIONS, draft.duration, "phút", c) { draft.duration = it }
    }

    // 3. Giới hạn video ngắn.
    StickerCard(c, contentPadding = 18.dp) {
        SectionHeader(
            emoji = "📺",
            color = PopPink,
            title = "Mỗi ngày lướt video tối đa bao nhiêu phút?",
            hint = "Đây là mức bạn tự đặt cho mình",
            c = c
        )
        Spacer(Modifier.height(12.dp))
        PopField(
            value = draft.dailyLimit,
            onValueChange = { draft.dailyLimit = it },
            hint = "Số phút, ví dụ 60",
            c = c,
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        )
    }

    // 4. Ít dùng: thu gọn mặc định.
    MoreOptions(title = "Cài đặt thêm (không bắt buộc)", c = c) {
        StickerCard(c, contentPadding = 18.dp) {
            SectionHeader(
                emoji = "🛡️",
                color = PopAzure,
                title = "Giờ bạn muốn tập trung",
                hint = "Ví dụ: 19:00 - 21:00",
                c = c
            )
            Spacer(Modifier.height(12.dp))
            PopField(
                value = draft.protectionHours,
                onValueChange = { draft.protectionHours = it },
                hint = "Ví dụ: 19:00 - 21:00",
                c = c,
                imeAction = ImeAction.Done
            )
        }
        StickerCard(c, contentPadding = 18.dp) {
            SectionHeader(
                emoji = "🎯",
                color = PopMint,
                title = "Bạn muốn thử kế hoạch này trong bao lâu?",
                c = c
            )
            Spacer(Modifier.height(12.dp))
            ChoiceRow(TARGET_DAY_OPTIONS, draft.targetDays, "ngày", c) { draft.targetDays = it }
        }
    }

    formError?.let { PopErrorBanner(it, c) }
    saveError?.let { PopErrorBanner(it, c) }

    PopButton(text = "Lưu kế hoạch", c = c, loading = saving, onClick = onSave)
}

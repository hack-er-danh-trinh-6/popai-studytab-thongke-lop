package com.health.pop.ui.prevention

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.health.pop.ui.common.PopAzure
import com.health.pop.ui.common.PopButton
import com.health.pop.ui.common.PopErrorBanner
import com.health.pop.ui.common.PopMood
import com.health.pop.ui.common.PopPage
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.StickerCard
import com.health.pop.ui.common.rememberPopPalette

/**
 * Màn "Bài tự đánh giá 20 câu" theo cùng phong cách với màn Đăng nhập / Đăng ký và các tab chính.
 * Màn này tự vẽ nền xanh dưới status bar (AppNavHost chỉ chừa chỗ cho thanh điều hướng dưới).
 */
@Composable
fun AssessmentScreen(
    viewModel: PreventionViewModel,
    onSubmitted: () -> Unit
) {
    val questions = viewModel.questions
    val answers by viewModel.answers.collectAsState()
    val submissionState by viewModel.submissionState.collectAsState()

    LaunchedEffect(submissionState) {
        if (submissionState is AssessmentSubmissionState.Success) {
            viewModel.markSubmissionHandled()
            onSubmitted()
        }
    }

    val c = rememberPopPalette()
    val submitting = submissionState is AssessmentSubmissionState.Submitting
    val submitError = (submissionState as? AssessmentSubmissionState.Error)?.message

    // ── tâm trạng + lời thoại của Pop ──
    val mood = if (submitError != null) PopMood.Oops else PopMood.Happy
    val bubble = when {
        submitting -> "Đang gửi bài của bạn…"
        submitError != null -> "Chưa gửi được. Xem thông báo bên dưới nhé."
        answers.isEmpty() -> "Chọn điểm từ 1 đến 5 cho mỗi câu nhé ✏️"
        answers.size >= 20 -> "Xong hết rồi! Bấm hoàn thành nhé 🎉"
        else -> "Đã trả lời ${answers.size}/20 câu, cố lên 💪"
    }

    PopPage(
        c = c,
        label = "POP AI  ·  TỰ ĐÁNH GIÁ",
        title = "Bài tự đánh giá 20 câu",
        bubble = bubble,
        mood = mood
    ) {
        PopText(
            "Thang điểm 1 (Rất ít/Không bao giờ) đến 5 (Rất thường xuyên/Rất nhiều)",
            c.textSoft,
            14
        )

        questions.forEachIndexed { index, question ->
            val selectedScore = answers[question.id] ?: 0
            val color = groupColor(question.group)

            StickerCard(c, contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IndexBadge(
                        text = "${index + 1}",
                        color = color,
                        c = c
                    )
                    Spacer(Modifier.width(8.dp))
                    PopText(
                        question.group.code,
                        c.textSoft,
                        12,
                        weight = FontWeight.ExtraBold,
                        spacing = 1f
                    )
                }
                Spacer(Modifier.height(8.dp))
                PopText(question.questionText, c.text, 15, weight = FontWeight.ExtraBold)
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { score ->
                        ScoreChoice(
                            score = score,
                            selected = selectedScore == score,
                            c = c,
                            onClick = { viewModel.setAnswer(question.id, score) }
                        )
                    }
                }
            }
        }

        submitError?.let { PopErrorBanner(it, c) }

        PopButton(
            text = "Hoàn thành bài đánh giá (${answers.size}/20)",
            c = c,
            loading = submitting,
            onClick = { viewModel.submitAssessment() }
        )
    }
}

/** Huy hiệu số thứ tự câu hỏi (hình tròn pastel). */
@Composable
private fun IndexBadge(text: String, color: Color, c: PopPalette) {
    Box(
        Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.3f).compositeOver(c.field))
            .border(2.dp, c.fieldLine, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        PopText(text, c.text, 13, weight = FontWeight.Black, align = TextAlign.Center)
    }
}

/** Ô chọn điểm 1–5: ô được chọn nền xanh, chữ trắng (giống ô chọn ở màn Kế hoạch). */
@Composable
private fun RowScope.ScoreChoice(
    score: Int,
    selected: Boolean,
    c: PopPalette,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .weight(1f)
            .height(52.dp)
            .clip(shape)
            .background(if (selected) PopAzure else c.card)
            .border(2.dp, if (selected) c.outline else c.fieldLine, shape)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        PopText(
            "$score",
            if (selected) Color.White else c.text,
            20,
            weight = FontWeight.Black,
            align = TextAlign.Center
        )
    }
}

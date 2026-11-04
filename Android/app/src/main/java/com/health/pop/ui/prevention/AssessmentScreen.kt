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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.unit.sp
import com.health.pop.ui.common.PopAzure
import com.health.pop.ui.common.PopButton
import com.health.pop.ui.common.PopErrorBanner
import com.health.pop.ui.common.PopMood
import com.health.pop.ui.common.PopPage
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.StickerCard
import com.health.pop.ui.common.rememberPopPalette
import com.health.pop.domain.model.AssessmentPhase

/**
 * Màn "Bài tự đánh giá 20 câu" theo cùng phong cách với màn Đăng nhập / Đăng ký và các tab chính.
 * Màn này tự vẽ nền xanh dưới status bar (AppNavHost chỉ chừa chỗ cho thanh điều hướng dưới).
 */
@Composable
fun AssessmentScreen(
    viewModel: PreventionViewModel,
    phase: AssessmentPhase,
    onSubmitted: () -> Unit
) {
    val questions = viewModel.questions
    val answers by viewModel.answers.collectAsState()
    val submissionState by viewModel.submissionState.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val openAnswers by viewModel.openAnswers.collectAsState()
    val questionCount = questions.size

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
        answers.size >= questionCount -> "Xong hết rồi! Bấm hoàn thành nhé 🎉"
        else -> "Đã trả lời ${answers.size}/$questionCount câu, cố lên 💪"
    }

    PopPage(
        c = c,
        label = "POP AI  ·  KHẢO SÁT NGHIÊN CỨU",
        title = "Phiếu ${phase.title}",
        bubble = bubble,
        mood = mood
    ) {
        PopText(
            "1 – Hoàn toàn không đồng ý · 2 – Không đồng ý · 3 – Phân vân · 4 – Đồng ý · 5 – Hoàn toàn đồng ý",
            c.textSoft,
            14
        )

        StickerCard(c, contentPadding = 16.dp) {
            PopText("Thông tin chung", c.text, 16, weight = FontWeight.Black)
            SurveyChoiceField("Giới tính", listOf("Nam", "Nữ"), profile["gender"], c) { viewModel.setProfile("gender", it) }
            SurveyChoiceField("Khối lớp", if (phase == AssessmentPhase.PRE) (6..12).map(Int::toString) else listOf("10", "11", "12"), profile["grade"], c) { viewModel.setProfile("grade", it) }
            if (phase == AssessmentPhase.PRE) {
                SurveyChoiceField("Trung bình mỗi ngày bạn sử dụng điện thoại", listOf("Dưới 2 giờ", "2–4 giờ", "4–6 giờ", "Trên 6 giờ"), profile["phoneHours"], c) { viewModel.setProfile("phoneHours", it) }
                SurveyChoiceField("Trung bình mỗi ngày bạn xem video ngắn", listOf("Dưới 30 phút", "30–60 phút", "1–2 giờ", "Trên 2 giờ"), profile["shortVideoHours"], c) { viewModel.setProfile("shortVideoHours", it) }
            } else {
                SurveyChoiceField("Bạn thuộc nhóm", listOf("Nhóm thực nghiệm", "Nhóm đối chứng"), profile["studyGroup"], c) { viewModel.setProfile("studyGroup", it) }
            }
        }

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

        StickerCard(c, contentPadding = 16.dp) {
            PopText("Câu hỏi mở", c.text, 16, weight = FontWeight.Black)
            val prompts = if (phase == AssessmentPhase.PRE) listOf(
                "Nguyên nhân khiến học sinh khó tập trung?" to "cause",
                "Bạn có mong muốn được hỗ trợ cải thiện tập trung sâu? (Có/Không)" to "support",
                "Giải pháp giúp hạn chế hội chứng Popcorn Brain?" to "solution"
            ) else listOf(
                "Điều bạn tâm đắc nhất sau khi tham gia Mô hình P.O.P?" to "highlight",
                "POP-AI đã hỗ trợ bạn như thế nào?" to "aiSupport",
                "Cần bổ sung hoặc cải tiến nội dung nào?" to "improvement"
            )
            prompts.forEach { (label, key) -> SurveyTextField(label, openAnswers[key].orEmpty(), c) { viewModel.setOpenAnswer(key, it) } }
        }

        PopButton(
            text = "Hoàn thành phiếu (${answers.size}/$questionCount)",
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

@Composable
private fun SurveyChoiceField(label: String, options: List<String>, selected: String?, c: PopPalette, onSelect: (String) -> Unit) {
    Spacer(Modifier.height(10.dp))
    PopText(label, c.textSoft, 13, weight = FontWeight.Bold)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (option == selected) PopAzure else c.card)
                    .border(1.dp, c.fieldLine, RoundedCornerShape(10.dp)).selectable(
                        selected = option == selected, interactionSource = remember { MutableInteractionSource() }, indication = null,
                        role = Role.RadioButton, onClick = { onSelect(option) }
                    ).padding(horizontal = 6.dp, vertical = 10.dp), contentAlignment = Alignment.Center
            ) { PopText(option, if (option == selected) Color.White else c.text, 12, weight = FontWeight.Bold, align = TextAlign.Center) }
        }
    }
}

@Composable
private fun SurveyTextField(label: String, value: String, c: PopPalette, onValueChange: (String) -> Unit) {
    Spacer(Modifier.height(10.dp))
    PopText(label, c.textSoft, 13, weight = FontWeight.Bold)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .background(c.field, RoundedCornerShape(12.dp))
            .border(1.dp, c.fieldLine, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        textStyle = androidx.compose.ui.text.TextStyle(color = c.text, fontSize = 14.sp),
        singleLine = false
    )
}

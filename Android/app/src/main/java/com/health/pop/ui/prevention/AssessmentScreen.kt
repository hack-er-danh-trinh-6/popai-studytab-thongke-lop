package com.health.pop.ui.prevention

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.health.pop.domain.model.AssessmentGroup
import com.health.pop.domain.model.AssessmentPhase
import com.health.pop.ui.common.PopAzure
import com.health.pop.ui.common.PopButton
import com.health.pop.ui.common.PopLightStatusBarIcons
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.StickerCard
import com.health.pop.ui.common.rememberPopPalette

@Composable
fun AssessmentScreen(
    viewModel: PreventionViewModel,
    phase: AssessmentPhase,
    onSubmitted: () -> Unit
) {
    val answers by viewModel.answers.collectAsState()
    val submissionState by viewModel.submissionState.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val openAnswers by viewModel.openAnswers.collectAsState()

    val questions = if (phase == AssessmentPhase.POST && profile["studyGroup"] != "Nhóm thực nghiệm") {
        viewModel.questions.filter { it.group != AssessmentGroup.DG }
    } else {
        viewModel.questions
    }
    val questionCount = questions.size
    val answeredCount = questions.count { answers.containsKey(it.id) }
    val submitting = submissionState is AssessmentSubmissionState.Submitting
    val submitError = (submissionState as? AssessmentSubmissionState.Error)?.message
    val c = rememberPopPalette()

    LaunchedEffect(submissionState) {
        if (submissionState is AssessmentSubmissionState.Success) {
            viewModel.markSubmissionHandled()
            onSubmitted()
        }
    }

    PopLightStatusBarIcons()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.card)
            .imePadding()
    ) {
        // Fixed header: remains visible while only the questionnaire list scrolls.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(c.heroTop, c.heroBottom)))
                .statusBarsPadding()
                .padding(horizontal = 18.dp, vertical = 12.dp)
        ) {
            PopText(
                "POP AI  ·  KHẢO SÁT NGHIÊN CỨU",
                Color.White.copy(alpha = 0.86f),
                11,
                weight = FontWeight.Bold,
                spacing = 0.7f,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PopText(
                    "Phiếu ${phase.title}",
                    Color.White,
                    22,
                    modifier = Modifier.weight(1f),
                    weight = FontWeight.Black,
                    maxLines = 1
                )
                PopText(
                    "$answeredCount/$questionCount",
                    Color.White,
                    16,
                    weight = FontWeight.ExtraBold,
                    align = TextAlign.End
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f))
            ) {
                if (questionCount > 0 && answeredCount > 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((answeredCount.toFloat() / questionCount).coerceIn(0f, 1f))
                            .height(7.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "intro") {
                StickerCard(c, contentPadding = 16.dp) {
                    PopText("Bạn thân mến!", c.text, 16, weight = FontWeight.Black)
                    PopText(
                        if (phase == AssessmentPhase.PRE) {
                            "Phiếu khảo sát này được thực hiện nhằm tìm hiểu thực trạng hội chứng Popcorn Brain (Não bỏng ngô) và khả năng tập trung sâu của học sinh THPT trong kỷ nguyên số. Mọi thông tin thu thập chỉ phục vụ mục đích nghiên cứu khoa học và được bảo mật hoàn toàn."
                        } else {
                            "Phiếu khảo sát này được thực hiện nhằm đánh giá hiệu quả của Mô hình P.O.P và Trợ lý ảo POP-AI sau quá trình thực nghiệm. Kết quả khảo sát chỉ phục vụ cho mục đích nghiên cứu khoa học và được bảo mật hoàn toàn."
                        },
                        c.textSoft,
                        14
                    )
                    Spacer(Modifier.height(6.dp))
                    PopText(
                        "Hướng dẫn: chọn một mức phù hợp nhất. 1 – Hoàn toàn không đồng ý; 2 – Không đồng ý; 3 – Phân vân; 4 – Đồng ý; 5 – Hoàn toàn đồng ý.",
                        c.textSoft,
                        13
                    )
                }
            }

            item(key = "profile-heading") {
                SectionHeading("PHẦN I. THÔNG TIN CHUNG", c)
            }

            item(key = "profile-fields") {
                StickerCard(c, contentPadding = 16.dp) {
                    SurveyChoiceField("Giới tính", listOf("Nam", "Nữ"), profile["gender"], c) {
                        viewModel.setProfile("gender", it)
                    }
                    SurveyChoiceField(
                        "Khối lớp",
                        if (phase == AssessmentPhase.PRE) (6..12).map(Int::toString) else listOf("10", "11", "12"),
                        profile["grade"],
                        c
                    ) { viewModel.setProfile("grade", it) }
                    if (phase == AssessmentPhase.PRE) {
                        SurveyChoiceField(
                            "Trung bình mỗi ngày bạn sử dụng điện thoại",
                            listOf("Dưới 2 giờ", "2–4 giờ", "4–6 giờ", "Trên 6 giờ"),
                            profile["phoneHours"],
                            c
                        ) { viewModel.setProfile("phoneHours", it) }
                        SurveyChoiceField(
                            "Trung bình mỗi ngày bạn xem video ngắn",
                            listOf("Dưới 30 phút", "30–60 phút", "1–2 giờ", "Trên 2 giờ"),
                            profile["shortVideoHours"],
                            c
                        ) { viewModel.setProfile("shortVideoHours", it) }
                    } else {
                        SurveyChoiceField(
                            "Bạn thuộc nhóm",
                            listOf("Nhóm thực nghiệm", "Nhóm đối chứng"),
                            profile["studyGroup"],
                            c
                        ) { viewModel.setProfile("studyGroup", it) }
                    }
                }
            }

            item(key = "questions-heading") {
                SectionHeading("PHẦN II. NỘI DUNG KHẢO SÁT", c)
            }

            itemsIndexed(
                items = questions,
                key = { _, question -> "question-${question.id}" }
            ) { index, question ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (index == 0 || questions[index - 1].group != question.group) {
                        SectionHeading(groupTitle(question.group), c)
                    }
                    QuestionCard(
                        number = index + 1,
                        question = question.questionText,
                        group = question.group,
                        selectedScore = answers[question.id] ?: 0,
                        c = c,
                        onScoreSelected = { score -> viewModel.setAnswer(question.id, score) }
                    )
                }
            }

            item(key = "open-heading") {
                SectionHeading("PHẦN III. CÂU HỎI MỞ", c)
            }

            item(key = "open-fields") {
                StickerCard(c, contentPadding = 16.dp) {
                    val prompts = if (phase == AssessmentPhase.PRE) listOf(
                        "Theo bạn, nguyên nhân nào khiến học sinh ngày nay khó tập trung trong học tập?" to "cause",
                        "Bạn có mong muốn được hỗ trợ cải thiện khả năng tập trung sâu không?" to "support",
                        "Theo bạn, giải pháp nào giúp học sinh hạn chế hội chứng Popcorn Brain hiệu quả nhất?" to "solution"
                    ) else listOf(
                        "Điều bạn tâm đắc nhất sau khi tham gia Mô hình P.O.P là gì?" to "highlight",
                        "Trợ lý ảo POP-AI đã hỗ trợ bạn như thế nào trong việc cải thiện khả năng tập trung?" to "aiSupport",
                        "Theo bạn, cần bổ sung hoặc cải tiến nội dung nào để mô hình đạt hiệu quả cao hơn?" to "improvement"
                    )
                    prompts.forEach { (label, answerKey) ->
                        if (phase == AssessmentPhase.PRE && answerKey == "support") {
                            SurveyChoiceField(label, listOf("Có", "Không"), openAnswers[answerKey], c) {
                                viewModel.setOpenAnswer(answerKey, it)
                            }
                        } else {
                            SurveyTextField(label, openAnswers[answerKey].orEmpty(), c) {
                                viewModel.setOpenAnswer(answerKey, it)
                            }
                        }
                    }
                }
            }
        }

        // Pinned footer: never scrolls away with the final question.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.card)
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 10.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(c.fieldLine))
            Spacer(Modifier.height(8.dp))
            PopText(
                if (answeredCount == questionCount) "Đã trả lời đủ câu hỏi" else "Còn thiếu ${questionCount - answeredCount} câu",
                c.textSoft,
                12,
                weight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            PopButton(
                text = if (submitting) "Đang gửi phiếu…" else "Gửi phiếu",
                c = c,
                loading = submitting,
                onClick = { viewModel.submitAssessment() }
            )
        }
    }

    submitError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSubmissionError,
            title = { Text("Chưa gửi được phiếu") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissSubmissionError) {
                    Text("Đã hiểu")
                }
            }
        )
    }
}

@Composable
private fun SectionHeading(text: String, c: PopPalette) {
    PopText(text, c.text, 14, weight = FontWeight.Black)
}

@Composable
private fun QuestionCard(
    number: Int,
    question: String,
    group: AssessmentGroup,
    selectedScore: Int,
    c: PopPalette,
    onScoreSelected: (Int) -> Unit
) {
    StickerCard(c, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IndexBadge(text = "$number", color = groupColor(group), c = c)
            Spacer(Modifier.width(8.dp))
            PopText(group.code, c.textSoft, 12, weight = FontWeight.ExtraBold, spacing = 1f)
        }
        PopText(question, c.text, 15, weight = FontWeight.ExtraBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (1..5).forEach { score ->
                ScoreChoice(
                    score = score,
                    selected = selectedScore == score,
                    c = c,
                    onClick = { onScoreSelected(score) }
                )
            }
        }
    }
}

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

@Composable
private fun RowScope.ScoreChoice(
    score: Int,
    selected: Boolean,
    c: PopPalette,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(if (selected) PopAzure else c.card)
            .border(2.dp, if (selected) c.outline else c.fieldLine, shape)
            .clickable(
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
private fun SurveyChoiceField(
    label: String,
    options: List<String>,
    selected: String?,
    c: PopPalette,
    onSelect: (String) -> Unit
) {
    Spacer(Modifier.height(10.dp))
    PopText(label, c.textSoft, 13, weight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option ->
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (option == selected) PopAzure else c.card)
                    .border(1.dp, c.fieldLine, RoundedCornerShape(10.dp))
                    .selectable(
                        selected = option == selected,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.RadioButton,
                        onClick = { onSelect(option) }
                    )
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                PopText(
                    option,
                    if (option == selected) Color.White else c.text,
                    12,
                    weight = FontWeight.Bold,
                    align = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SurveyTextField(
    label: String,
    value: String,
    c: PopPalette,
    onValueChange: (String) -> Unit
) {
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

private fun groupTitle(group: AssessmentGroup): String = when (group) {
    AssessmentGroup.VN -> "A. THÓI QUEN SỬ DỤNG VIDEO NGẮN (VN)"
    AssessmentGroup.PT -> "B. PHỤ THUỘC THIẾT BỊ SỐ (PT)"
    AssessmentGroup.PB -> "C. BIỂU HIỆN HỘI CHỨNG POPCORN BRAIN (PB)"
    AssessmentGroup.TT -> "D. KHẢ NĂNG TẬP TRUNG SÂU (TT)"
    AssessmentGroup.DG -> "E. ĐÁNH GIÁ HIỆU QUẢ MÔ HÌNH P.O.P VÀ TRỢ LÝ ẢO POP-AI (DG)"
}

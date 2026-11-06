package com.health.pop.ui.prevention

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.health.pop.domain.model.AssessmentGroup
import com.health.pop.domain.model.AssessmentPhase
import com.health.pop.ui.common.PopAzure
import com.health.pop.ui.common.PopBadge
import com.health.pop.ui.common.PopButton
import com.health.pop.ui.common.PopMint
import com.health.pop.ui.common.PopMood
import com.health.pop.ui.common.PopPage
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopPink
import com.health.pop.ui.common.PopSun
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.StickerCard
import com.health.pop.ui.common.rememberPopPalette

/** Màu nhận diện của từng nhóm chỉ số, dùng chung cho các màn tự đánh giá. */
internal fun groupColor(group: AssessmentGroup): Color = when (group) {
    AssessmentGroup.VN -> PopPink
    AssessmentGroup.PT -> PopAzure
    AssessmentGroup.PB -> PopSun
    AssessmentGroup.TT -> PopMint
    AssessmentGroup.DG -> PopPink
}

/**
 * Màn "Hiểu về Popcorn Brain" theo cùng phong cách với màn Đăng nhập / Đăng ký và các tab chính.
 * Màn này tự vẽ nền xanh dưới status bar (AppNavHost chỉ chừa chỗ cho thanh điều hướng dưới).
 */
@Composable
fun PreventionScreen(
    viewModel: PreventionViewModel,
    onStartAssessment: (AssessmentPhase) -> Unit
) {
    val history by viewModel.historyState.collectAsState()
    val historyLoading by viewModel.historyLoading.collectAsState()
    val historyLoaded by viewModel.historyLoaded.collectAsState()
    val historyError by viewModel.historyError.collectAsState()
    val c = rememberPopPalette()

    PopPage(
        c = c,
        label = "POP AI  ·  TỰ ĐÁNH GIÁ",
        title = "Hiểu về Popcorn Brain",
        bubble = if (history.isEmpty()) "Hãy bắt đầu bằng phiếu trước thực nghiệm nhé 🍿" else "Bạn có thể làm phiếu sau thực nghiệm khi hoàn tất chương trình 📈",
        mood = PopMood.Happy
    ) {
        PopText("Nhận diện xao nhãng & Rèn luyện sự tập trung", c.textSoft, 14)

        // Popcorn Brain là gì?
        StickerCard(c, contentPadding = 18.dp) {
            PrevSectionHeader("🧠", PopPink, "Popcorn Brain là gì?", c)
            Spacer(Modifier.height(12.dp))
            PopText(
                "Khi thường xuyên xem video ngắn (TikTok, Reels, Shorts), não bộ quen với nhịp kích thích siêu nhanh, nhảy liên tục như những hạt bỏng ngô (popcorn). Khi quay lại đọc sách hoặc giải toán, não sẽ thấy 'chậm' và sốt ruột.",
                c.text,
                15
            )
            Spacer(Modifier.height(10.dp))
            PopText(
                "POP AI giúp bạn rèn luyện lại khả năng tập trung sâu từng bước một — không cấm dùng thiết bị, không ép buộc, luôn nhẹ nhàng khích lệ.",
                c.accent,
                15,
                weight = FontWeight.Bold
            )
        }

        // Bài tự đánh giá
        StickerCard(c, contentPadding = 18.dp) {
            PrevSectionHeader("📝", PopSun, "Hai đợt khảo sát nghiên cứu", c)
            Spacer(Modifier.height(12.dp))
            PopText(
                "Phiếu trước gồm 20 câu. Phiếu sau gồm 20 câu chung; học sinh nhóm thực nghiệm trả lời thêm 5 câu đánh giá mô hình P.O.P và POP-AI.",
                c.text,
                15
            )
            Spacer(Modifier.height(16.dp))
            when {
                historyError != null -> {
                    PopText(
                        "Chưa kiểm tra được các phiếu đã làm. Hãy thử lại khi có mạng; phiếu sẽ bị khóa cho đến khi kiểm tra xong để tránh gửi trùng.",
                        c.textSoft,
                        13
                    )
                    Spacer(Modifier.height(10.dp))
                    PopButton(
                        text = "Thử kiểm tra lại",
                        c = c,
                        loading = historyLoading,
                        onClick = viewModel::loadHistory
                    )
                }
                !historyLoaded -> {
                    PopText("Đang kiểm tra lịch sử khảo sát của bạn…", c.textSoft, 14, weight = FontWeight.Bold)
                }
                else -> {
                    val preCompleted = history.any { it.phase == AssessmentPhase.PRE }
                    val postCompleted = history.any { it.phase == AssessmentPhase.POST }
                    SurveyPhaseButton("Làm phiếu trước thực nghiệm", preCompleted, c) {
                        onStartAssessment(AssessmentPhase.PRE)
                    }
                    Spacer(Modifier.height(10.dp))
                    SurveyPhaseButton("Làm phiếu sau thực nghiệm", postCompleted, c) {
                        onStartAssessment(AssessmentPhase.POST)
                    }
                }
            }
        }

        // Lịch sử đánh giá
        PopText("Lịch sử đánh giá của bạn", c.text, 18, weight = FontWeight.Black)

        if (history.isEmpty()) {
            StickerCard(c, contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PopText("🌱", c.text, 20)
                    Spacer(Modifier.width(10.dp))
                    PopText(
                        "Chưa có lượt đánh giá nào. Bạn hãy làm bài đánh giá đầu tiên nhé!",
                        c.textSoft,
                        14,
                        Modifier.weight(1f)
                    )
                }
            }
        } else {
            history.forEach { item ->
                StickerCard(c, contentPadding = 16.dp) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        PopText(item.completedAt, c.accent, 15, weight = FontWeight.ExtraBold)
                        Spacer(Modifier.width(12.dp))
                        PopText(
                            item.note,
                            c.textSoft,
                            13,
                            Modifier.weight(1f),
                            align = TextAlign.End
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScoreChip(AssessmentGroup.VN, item.vnScore, c)
                        ScoreChip(AssessmentGroup.PT, item.ptScore, c)
                        ScoreChip(AssessmentGroup.PB, item.pbScore, c)
                        ScoreChip(AssessmentGroup.TT, item.ttScore, c)
                    }
                }
            }
        }
    }
}

@Composable
private fun SurveyPhaseButton(
    text: String,
    completed: Boolean,
    c: PopPalette,
    onClick: () -> Unit
) {
    if (completed) {
        StickerCard(c, contentPadding = 16.dp) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                PopText("✓", PopMint, 20, weight = FontWeight.Black)
                Spacer(Modifier.width(10.dp))
                PopText(
                    "$text — đã hoàn thành, không thể làm lại",
                    c.textSoft,
                    14,
                    Modifier.weight(1f),
                    FontWeight.Bold
                )
            }
        }
    } else {
        PopButton(text = text, c = c, onClick = onClick)
    }
}

@Composable
private fun PrevSectionHeader(emoji: String, color: Color, title: String, c: PopPalette) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PopBadge(emoji, color, c, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        PopText(title, c.text, 16, Modifier.weight(1f), FontWeight.ExtraBold)
    }
}

/** Ô điểm nhỏ cho một nhóm chỉ số: mã nhóm + điểm/5. */
@Composable
private fun RowScope.ScoreChip(group: AssessmentGroup, score: Float, c: PopPalette) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(groupColor(group).copy(alpha = 0.2f).compositeOver(c.card))
            .border(2.dp, c.fieldLine, shape)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PopText(group.code, c.textSoft, 12, weight = FontWeight.ExtraBold, align = TextAlign.Center)
        PopText("$score/5", c.text, 15, weight = FontWeight.Black, align = TextAlign.Center)
    }
}

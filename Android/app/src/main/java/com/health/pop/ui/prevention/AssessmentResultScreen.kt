package com.health.pop.ui.prevention

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.health.pop.domain.model.AssessmentGroup
import com.health.pop.ui.common.PopBadge
import com.health.pop.ui.common.PopButton
import com.health.pop.ui.common.PopErrorBanner
import com.health.pop.ui.common.PopMood
import com.health.pop.ui.common.PopPage
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopSun
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.StickerCard
import com.health.pop.ui.common.rememberPopPalette

/**
 * Màn "Kết quả tự đánh giá" theo cùng phong cách với màn Đăng nhập / Đăng ký và các tab chính.
 * Màn này tự vẽ nền xanh dưới status bar (AppNavHost chỉ chừa chỗ cho thanh điều hướng dưới).
 */
@Composable
fun AssessmentResultScreen(
    viewModel: PreventionViewModel,
    onDone: () -> Unit
) {
    val submissionState by viewModel.submissionState.collectAsState()

    val result = (submissionState as? AssessmentSubmissionState.Success)?.result
    val error = (submissionState as? AssessmentSubmissionState.Error)?.message

    val c = rememberPopPalette()

    PopPage(
        c = c,
        label = "POP AI  ·  KẾT QUẢ",
        title = "Kết quả tự đánh giá",
        bubble = when {
            error != null -> "Chưa có kết quả. Xem thông báo bên dưới nhé."
            result == null -> "Chưa có kết quả nào để hiển thị."
            else -> "Làm tốt lắm! Cùng xem kết quả nhé 🎉"
        },
        mood = if (error != null) PopMood.Oops else PopMood.Happy
    ) {
        error?.let { PopErrorBanner(it, c) }

        PopText("Điểm trung bình theo nhóm (Thang điểm 1 - 5)", c.textSoft, 14)

        if (result != null) {
            ScoreCard(AssessmentGroup.VN, "🎬", "Thói quen video ngắn", result.vnScore, c)
            ScoreCard(AssessmentGroup.PT, "📱", "Phụ thuộc thiết bị", result.ptScore, c)
            ScoreCard(AssessmentGroup.PB, "🧩", "Phân mảnh chú ý", result.pbScore, c)
            ScoreCard(AssessmentGroup.TT, "🎯", "Tập trung sâu", result.ttScore, c)

            StickerCard(c, contentPadding = 18.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PopBadge("📌", PopSun, c, size = 38.dp)
                    Spacer(Modifier.width(12.dp))
                    PopText("Lưu ý quan trọng", c.text, 16, Modifier.weight(1f), FontWeight.ExtraBold)
                }
                Spacer(Modifier.height(10.dp))
                PopText(result.note, c.textSoft, 14)
            }
        }

        PopButton(
            text = "Quay về màn hình chính",
            c = c,
            onClick = {
                viewModel.resetSubmissionState()
                onDone()
            }
        )
    }
}

/** Thẻ điểm một nhóm: tên nhóm, điểm /5, thanh tiến độ và mô tả. */
@Composable
private fun ScoreCard(
    group: AssessmentGroup,
    emoji: String,
    name: String,
    score: Float,
    c: PopPalette
) {
    val color = groupColor(group)
    val fraction = (score / 5f).coerceIn(0f, 1f)
    StickerCard(c, contentPadding = 18.dp) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            PopBadge(emoji, color, c, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                PopText("${group.code} - $name", c.text, 16, weight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.width(8.dp))
            PopText("$score / 5", c.accent, 18, weight = FontWeight.Black, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(c.fieldLine)
        ) {
            if (fraction > 0f) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction)
                        .height(12.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        PopText(group.description, c.textSoft, 14)
    }
}

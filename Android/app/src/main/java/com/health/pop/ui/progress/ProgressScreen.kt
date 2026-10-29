package com.health.pop.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
import com.health.pop.domain.usecase.FormatDurationUseCase
import com.health.pop.ui.common.PopAzure
import com.health.pop.ui.common.PopBadge
import com.health.pop.ui.common.PopErrorBanner
import com.health.pop.ui.common.PopLoadingDots
import com.health.pop.ui.common.PopMint
import com.health.pop.ui.common.PopMood
import com.health.pop.ui.common.PopPage
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopPink
import com.health.pop.ui.common.PopSun
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.StickerCard
import com.health.pop.ui.common.rememberPopPalette
import com.health.pop.ui.onboarding.LocalOnboarding

private const val TIP_PROGRESS = "progress"

/**
 * Màn "Tiến bộ cá nhân" theo cùng phong cách với màn Đăng nhập / Đăng ký, Hôm nay, Kế hoạch, Tập trung và Nhật ký.
 * Màn này tự vẽ nền xanh dưới status bar (AppNavHost chỉ chừa chỗ cho thanh điều hướng dưới).
 */
@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel
) {
    val selectedDays by viewModel.selectedDays.collectAsState()
    val summary by viewModel.summaryState.collectAsState()
    val error by viewModel.errorState.collectAsState()
    val durationFormatter = remember { FormatDurationUseCase() }

    val c = rememberPopPalette()
    val onboarding = LocalOnboarding.current
    val showTip = onboarding != null &&
        !onboarding.isTipDismissed(TIP_PROGRESS) &&
        !onboarding.isTourVisible

    // ── tâm trạng + lời thoại của Pop ──
    val data = summary
    val mood = if (error != null) PopMood.Oops else PopMood.Happy
    val bubble = when {
        error != null -> "Chưa tải được số liệu. Xem thông báo bên dưới nhé."
        data == null -> "Đang tải hành trình của bạn…"
        data.focusStreakDays > 0 -> "Streak ${data.focusStreakDays} ngày rồi, giỏi quá! 🔥"
        else -> "Cùng xem bạn đã tiến bộ thế nào nhé 📈"
    }

    PopPage(
        c = c,
        label = "POP AI  ·  TIẾN BỘ",
        title = "Tiến bộ cá nhân",
        bubble = bubble,
        mood = mood
    ) {
        if (showTip) {
            ProgressTip(c = c, onDismiss = { onboarding?.dismissTip(TIP_PROGRESS) })
        }

        PopText("Theo dõi sự thay đổi & Hành trình rèn luyện", c.textSoft, 14)

        // Khoảng thời gian: 7, 14, 21, 30 ngày
        StickerCard(c, contentPadding = 18.dp) {
            ProgressSectionHeader("📅", PopAzure, "Khoảng thời gian", c)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(7, 14, 21, 30).forEach { days ->
                    DaysChoice(
                        days = days,
                        selected = selectedDays == days,
                        c = c,
                        onClick = { viewModel.loadProgress(days) }
                    )
                }
            }
        }

        error?.let { message ->
            PopErrorBanner(
                message = message,
                c = c,
                actionLabel = "Thử lại",
                onAction = { viewModel.loadProgress(selectedDays) }
            )
        }

        if (data == null && error == null) {
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                PopLoadingDots(c.accent)
            }
        }

        if (data != null) {
            // Thống kê tổng quan
            StickerCard(c, contentPadding = 18.dp) {
                ProgressSectionHeader("📊", PopAzure, "Thống kê $selectedDays ngày qua", c)
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        emoji = "⏱️",
                        color = PopAzure,
                        value = durationFormatter.formatMinutesToText(data.totalFocusMinutes),
                        label = "Tổng phút tập trung",
                        c = c
                    )
                    StatTile(
                        emoji = "📺",
                        color = PopPink,
                        value = "${data.avgShortVideoMinutes} phút",
                        label = "TB lướt video/ngày",
                        c = c
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        emoji = "✅",
                        color = PopMint,
                        value = "${data.completedSessionsCount} phiên",
                        label = "Số phiên hoàn thành",
                        c = c
                    )
                    StatTile(
                        emoji = "🔔",
                        color = PopSun,
                        value = "${data.totalInterruptions} lần",
                        label = "Số lần ngắt quãng",
                        c = c
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        emoji = "🔥",
                        color = PopSun,
                        value = "${data.focusStreakDays} ngày",
                        label = "Streak tập trung 1 giờ",
                        c = c
                    )
                }
            }

            // Điểm làm tốt
            StickerCard(c, contentPadding = 18.dp) {
                ProgressSectionHeader("👍", PopMint, "Điểm làm tốt của bạn", c)
                Spacer(Modifier.height(10.dp))
                BulletList(
                    items = data.highlights,
                    empty = "Chưa có điểm nổi bật nào. Dùng app đều vài ngày nhé!",
                    c = c
                )
            }

            // Gợi ý điều chỉnh
            StickerCard(c, contentPadding = 18.dp) {
                ProgressSectionHeader("💡", PopSun, "Gợi ý điều chỉnh", c)
                Spacer(Modifier.height(10.dp))
                BulletList(
                    items = data.areasToAdjust,
                    empty = "Chưa có gợi ý nào cho khoảng thời gian này.",
                    c = c
                )
            }
        }
    }
}

/** Gợi ý "màn này dùng để làm gì": hiện một lần cho mỗi tài khoản đến khi bấm "Đã hiểu". */
@Composable
private fun ProgressTip(c: PopPalette, onDismiss: () -> Unit) {
    StickerCard(
        c,
        fill = PopSun.copy(alpha = 0.25f).compositeOver(c.card),
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PopText("💡", c.text, 18)
            Spacer(Modifier.width(10.dp))
            Column {
                PopText("Đọc màn Tiến bộ như thế nào?", c.text, 15, weight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                PopText(
                    "Chọn 7, 14, 21 hoặc 30 ngày để xem tổng phút tập trung, số phiên hoàn thành và chuỗi ngày tập trung. Dùng app đều vài ngày thì số liệu sẽ đầy đủ hơn.",
                    c.text,
                    14
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PopText(
                "Đã hiểu",
                c.accent,
                14,
                Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                FontWeight.ExtraBold
            )
        }
    }
}

@Composable
private fun ProgressSectionHeader(
    emoji: String,
    color: Color,
    title: String,
    c: PopPalette
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PopBadge(emoji, color, c, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        PopText(title, c.text, 16, Modifier.weight(1f), FontWeight.ExtraBold)
    }
}

/** Ô chọn số ngày: ô được chọn nền xanh, chữ trắng (giống ô chọn ở màn Kế hoạch). */
@Composable
private fun RowScope.DaysChoice(
    days: Int,
    selected: Boolean,
    c: PopPalette,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .height(60.dp)
            .clip(shape)
            .background(if (selected) PopAzure else c.field)
            .border(2.dp, if (selected) c.outline else c.fieldLine, shape)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PopText(
            "$days",
            if (selected) Color.White else c.text,
            20,
            weight = FontWeight.Black,
            align = TextAlign.Center
        )
        PopText(
            "ngày",
            if (selected) Color.White.copy(alpha = 0.85f) else c.textSoft,
            11,
            weight = FontWeight.SemiBold,
            align = TextAlign.Center
        )
    }
}

/** Ô số liệu pastel: emoji + giá trị lớn + nhãn nhỏ. */
@Composable
private fun RowScope.StatTile(
    emoji: String,
    color: Color,
    value: String,
    label: String,
    c: PopPalette
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(color.copy(alpha = 0.18f).compositeOver(c.card))
            .border(2.dp, c.fieldLine, shape)
            .padding(12.dp)
    ) {
        PopText(emoji, c.text, 20)
        Spacer(Modifier.height(6.dp))
        PopText(value, c.text, 20, weight = FontWeight.Black)
        Spacer(Modifier.height(2.dp))
        PopText(label, c.textSoft, 12, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun BulletList(items: List<String>, empty: String, c: PopPalette) {
    if (items.isEmpty()) {
        PopText(empty, c.textSoft, 14)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(c.accent)
                )
                Spacer(Modifier.width(10.dp))
                PopText(item, c.text, 15, Modifier.weight(1f), FontWeight.SemiBold)
            }
        }
    }
}

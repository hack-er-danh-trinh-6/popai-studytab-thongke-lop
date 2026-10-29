package com.health.pop.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.health.pop.domain.usecase.FormatDurationUseCase
import com.health.pop.ui.common.PopAzure
import com.health.pop.ui.common.PopBadge
import com.health.pop.ui.common.PopButton
import com.health.pop.ui.common.PopCoral
import com.health.pop.ui.common.PopInk
import com.health.pop.ui.common.PopLightStatusBarIcons
import com.health.pop.ui.common.PopLoadingDots
import com.health.pop.ui.common.PopMascot
import com.health.pop.ui.common.PopMint
import com.health.pop.ui.common.PopMood
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopPink
import com.health.pop.ui.common.PopSpeechBubble
import com.health.pop.ui.common.PopSun
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.StickerCard
import com.health.pop.ui.common.overlapTop
import com.health.pop.ui.common.popHeroBubbles
import com.health.pop.ui.common.rememberPopPalette
import com.health.pop.ui.onboarding.LocalOnboarding

private const val MAX_VISIBLE_TASKS = 4

private const val STEP_PLAN = "plan"
private const val STEP_FOCUS = "focus"
private const val STEP_JOURNAL = "journal"
private const val TIP_GETTING_STARTED = "home_getting_started"

/**
 * Màn "Hôm nay" theo cùng phong cách với màn Đăng nhập / Đăng ký: nền xanh + Pop + bong bóng thoại,
 * thẻ nội dung bo góc chồng lên phần đầu, các khối dạng "sticker" (viền đậm, bóng cứng).
 *
 * Màn này tự vẽ nền xanh dưới status bar (AppNavHost chỉ chừa chỗ cho thanh điều hướng dưới).
 */
@Composable
fun TodayHomeScreen(
    viewModel: HomeViewModel,
    onNavigateToFocus: () -> Unit,
    onNavigateToJournal: () -> Unit,
    onNavigateToAssessment: () -> Unit,
    onNavigateToPlan: () -> Unit,
    onNavigateToClassroom: () -> Unit,
    onNavigateToTracker: () -> Unit,
    onShowGuide: () -> Unit
) {
    val user by viewModel.currentUser.collectAsState()
    val plan by viewModel.currentPlan.collectAsState()
    val journal by viewModel.todayJournal.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val durationFormatter = remember { FormatDurationUseCase() }

    // Phiên tập trung đã hoàn thành (đo thật) và số phút tự khai trong nhật ký có thể trùng nhau,
    // nên lấy giá trị lớn hơn thay vì cộng dồn.
    val focusMinutes = maxOf(uiState.sessionFocusMinutes, journal.focusMinutes)
    val focusTime = durationFormatter.formatMinutesToText(focusMinutes)

    val displayName = user?.fullName?.trim().takeUnless { it.isNullOrBlank() }
    // Đang tải lần đầu: chưa biết người dùng đã có kế hoạch hay chưa nên không hiện trạng thái "chưa có kế hoạch".
    val planPending = uiState.isLoading && !plan.hasSavedPlan

    // Checklist "Bắt đầu với POP AI": chỉ ghi nhận / hiển thị khi đã tải xong dữ liệu để người dùng cũ không thấy nó nhấp nháy.
    val onboarding = LocalOnboarding.current
    val journalSaved = journal.id.isNotBlank() || journal.focusMinutes > 0 ||
        journal.distractors.isNotEmpty() || journal.tomorrowAdjustment.isNotBlank()
    val dataReady = !uiState.isLoading && !uiState.planUnavailable
    if (onboarding != null && dataReady) {
        LaunchedEffect(plan.hasSavedPlan) { if (plan.hasSavedPlan) onboarding.markStepDone(STEP_PLAN) }
        LaunchedEffect(focusMinutes > 0) { if (focusMinutes > 0) onboarding.markStepDone(STEP_FOCUS) }
        LaunchedEffect(journalSaved) { if (journalSaved) onboarding.markStepDone(STEP_JOURNAL) }
    }
    val planDone = plan.hasSavedPlan || onboarding?.isStepDone(STEP_PLAN) == true
    val focusDone = focusMinutes > 0 || onboarding?.isStepDone(STEP_FOCUS) == true
    val journalDone = journalSaved || onboarding?.isStepDone(STEP_JOURNAL) == true
    val showGettingStarted = onboarding != null && dataReady &&
        !onboarding.isTipDismissed(TIP_GETTING_STARTED) &&
        !(planDone && focusDone && journalDone)
    val gettingStartedSteps = listOf(
        GettingStartedStep("Tạo kế hoạch cá nhân", "Đặt giới hạn video và vài việc nhỏ", planDone, onNavigateToPlan),
        GettingStartedStep("Chạy phiên tập trung đầu tiên", "Bấm giờ một phiên ngắn cho một việc", focusDone, onNavigateToFocus),
        GettingStartedStep("Ghi nhật ký 3 phút", "Nhìn lại hôm nay, chọn một điều chỉnh cho ngày mai", journalDone, onNavigateToJournal)
    )

    val c = rememberPopPalette()
    // Phần đầu luôn là nền xanh nên icon status bar cần màu sáng (cả chế độ sáng lẫn tối).
    PopLightStatusBarIcons()

    // ── tâm trạng + lời thoại của Pop ──
    val hasError = !uiState.errorMessage.isNullOrBlank()
    val mood = if (hasError) PopMood.Oops else PopMood.Happy
    val bubble = when {
        hasError -> "Có trục trặc rồi. Bấm \"Thử lại\" nhé."
        uiState.isLoading -> "Đợi mình chút, đang chuẩn bị hôm nay…"
        focusMinutes > 0 -> "Hôm nay bạn đã tập trung $focusTime rồi, giỏi quá! 🎉"
        !plan.hasSavedPlan -> "Bắt đầu bằng một kế hoạch nhỏ nhé ✨"
        else -> "Sẵn sàng cho một phiên tập trung chưa? 🎯"
    }

    // ── hiệu ứng vào màn hình: chỉ lần đầu (rememberSaveable để quay lại tab không chạy lại) ──
    val isPreview = LocalInspectionMode.current
    var entered by rememberSaveable { mutableStateOf(isPreview) }
    LaunchedEffect(Unit) { entered = true }
    val popIn by animateFloatAsState(
        if (entered) 1f else 0f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow),
        label = "homePopIn",
    )
    val cardIn by animateFloatAsState(
        if (entered) 1f else 0f,
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "homeCardIn",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.card)
            .verticalScroll(rememberScrollState())
    ) {
        // ═════════ PHẦN ĐẦU: nền xanh + lời chào + Pop ═════════
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(c.heroTop, c.heroBottom)))
                .popHeroBubbles()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // weight(1f) để tên dài tự xuống dòng/cắt bớt thay vì đẩy avatar ra khỏi màn hình.
                Column(modifier = Modifier.weight(1f)) {
                    PopText(
                        "POP AI  ·  HÔM NAY",
                        Color.White.copy(alpha = 0.85f),
                        12,
                        weight = FontWeight.Bold,
                        spacing = 1.1f,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(4.dp))
                    PopText(
                        "Xin chào, ${displayName ?: "Bạn"} ✨",
                        Color.White,
                        24,
                        weight = FontWeight.Black,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                HelpButton(onClick = onShowGuide)
                Spacer(Modifier.width(4.dp))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(PopSun)
                        .border(2.dp, PopInk, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    PopText(
                        displayName?.first()?.uppercase() ?: "P",
                        PopInk,
                        18,
                        weight = FontWeight.Black
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            PopSpeechBubble(bubble)
            PopMascot(
                mood,
                Modifier
                    .size(112.dp)
                    .graphicsLayer {
                        scaleX = popIn
                        scaleY = popIn
                    }
            )
        }

        // ═════════ THẺ NỘI DUNG ═════════
        Column(
            modifier = Modifier
                .overlapTop(28.dp)
                .graphicsLayer {
                    translationY = (1f - cardIn) * 90.dp.toPx()
                    alpha = cardIn.coerceIn(0f, 1f)
                }
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                .background(c.card)
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (uiState.isLoading) {
                PopLoadingDots(
                    c.accent,
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(top = 6.dp, bottom = 4.dp)
                )
            }
            uiState.errorMessage?.let { message ->
                HomeErrorBanner(message = message, onRetry = viewModel::refresh, c = c)
            }

            // Thời gian tập trung hôm nay: thẻ vàng nổi bật, chữ luôn là màu mực.
            StickerCard(c, fill = PopSun, contentPadding = 18.dp) {
                PopText(
                    "⏱️  Thời gian tập trung hôm nay",
                    PopInk,
                    14,
                    weight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                PopText(focusTime, PopInk, 40, weight = FontWeight.Black, spacing = -0.5f)
                PopText(
                    "Mỗi phút tập trung là một bước tiến.",
                    PopInk.copy(alpha = 0.72f),
                    14
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.55f))
                        .padding(vertical = 12.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HeroStat(
                        modifier = Modifier.weight(1f),
                        value = if (plan.hasSavedPlan) "${plan.studyTasks.size}" else "—",
                        label = when {
                            plan.hasSavedPlan -> "Việc hôm nay"
                            planPending -> "Đang tải…"
                            uiState.planUnavailable -> "Chưa tải được"
                            else -> "Chưa có kế hoạch"
                        }
                    )
                    StatDivider()
                    HeroStat(
                        modifier = Modifier.weight(1f),
                        value = if (plan.hasSavedPlan) "${plan.dailyShortVideoLimitMinutes} phút" else "—",
                        label = "Giới hạn video"
                    )
                    StatDivider()
                    HeroStat(
                        modifier = Modifier.weight(1f),
                        value = if (plan.hasSavedPlan && plan.focusProtectionHours.isNotBlank()) plan.focusProtectionHours else "—",
                        label = "Giờ bảo vệ"
                    )
                }
            }

            if (showGettingStarted) {
                GettingStartedCard(
                    steps = gettingStartedSteps,
                    onDismiss = { onboarding?.dismissTip(TIP_GETTING_STARTED) },
                    c = c
                )
            }

            Column {
                PopText("Bắt đầu nhẹ nhàng", c.text, 20, weight = FontWeight.Black)
                Spacer(Modifier.height(2.dp))
                PopText("Chọn một việc nhỏ cho hôm nay", c.textSoft, 14)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    c = c,
                    title = "Tập trung",
                    subtitle = "Bắt đầu một phiên",
                    emoji = "⏱️",
                    badgeColor = PopAzure,
                    onClick = onNavigateToFocus
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    c = c,
                    title = "Nhật ký nhanh",
                    subtitle = "Ghi lại cảm nhận",
                    emoji = "📓",
                    badgeColor = PopPink,
                    onClick = onNavigateToJournal
                )
            }

            StickerCard(c, contentPadding = 18.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PopText(
                        "📚  Nhiệm vụ học tập",
                        c.text,
                        18,
                        Modifier.weight(1f),
                        FontWeight.Black
                    )
                    Spacer(Modifier.width(8.dp))
                    CountPill(
                        text = when {
                            plan.hasSavedPlan -> "${plan.studyTasks.size} việc"
                            planPending -> "Đang tải"
                            uiState.planUnavailable -> "Lỗi tải"
                            else -> "Chưa lưu"
                        },
                        c = c
                    )
                }
                Spacer(Modifier.height(10.dp))
                when {
                    plan.studyTasks.isNotEmpty() -> {
                        plan.studyTasks.take(MAX_VISIBLE_TASKS).forEach { task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Vòng tròn rỗng: nhiệm vụ chưa hoàn thành.
                                Box(
                                    Modifier
                                        .size(22.dp)
                                        .border(2.5.dp, c.accent, CircleShape)
                                )
                                Spacer(Modifier.width(12.dp))
                                PopText(
                                    task,
                                    c.text,
                                    15,
                                    Modifier.weight(1f),
                                    FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (plan.studyTasks.size > MAX_VISIBLE_TASKS) {
                            PopText(
                                "Xem tất cả ${plan.studyTasks.size} việc →",
                                c.accent,
                                14,
                                Modifier
                                    .clip(CircleShape)
                                    .clickable(role = Role.Button, onClick = onNavigateToPlan)
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                FontWeight.ExtraBold
                            )
                        }
                    }
                    planPending -> HomeHint("Đang tải kế hoạch của bạn…", c)
                    uiState.planUnavailable -> HomeHint("Chưa tải được kế hoạch. Hãy bấm \"Thử lại\" ở phía trên.", c)
                    plan.hasSavedPlan -> {
                        HomeHint("Chưa có nhiệm vụ nào. Hãy chọn một việc nhỏ để bắt đầu!", c)
                        Spacer(Modifier.height(12.dp))
                        PopButton("Thêm nhiệm vụ", c, onNavigateToPlan)
                    }
                    else -> {
                        HomeHint("Bạn chưa lưu kế hoạch cá nhân. Hãy tạo kế hoạch để theo dõi mục tiêu của mình.", c)
                        Spacer(Modifier.height(12.dp))
                        PopButton("Tạo kế hoạch", c, onNavigateToPlan)
                    }
                }
            }

            LinkCard(
                c = c,
                emoji = "🏫",
                badgeColor = PopAzure,
                title = "Lớp học",
                subtitle = "Chọn lớp, xem thông báo từ giáo viên",
                clickLabel = "Mở lớp học",
                onClick = onNavigateToClassroom
            )

            LinkCard(
                c = c,
                emoji = "⏱️",
                badgeColor = PopSun,
                title = "Theo dõi TikTok, YouTube",
                subtitle = "Đặt hạn mức mỗi ngày, nhận nhắc nghỉ ngơi",
                clickLabel = "Mở theo dõi thời gian dùng app",
                onClick = onNavigateToTracker
            )

            LinkCard(
                c = c,
                emoji = "🧠",
                badgeColor = PopMint,
                title = "Hiểu thêm về sự chú ý",
                subtitle = "Bài tự đánh giá ngắn · 20 câu",
                clickLabel = "Mở bài đánh giá",
                fill = PopMint.copy(alpha = 0.22f).compositeOver(c.card),
                onClick = onNavigateToAssessment
            )
        }
    }
}

@Composable
private fun HomeErrorBanner(message: String, onRetry: () -> Unit, c: PopPalette) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PopCoral.copy(alpha = 0.12f))
            .border(1.5.dp, PopCoral, shape)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PopText("😕", c.text, 18)
        Spacer(Modifier.width(10.dp))
        PopText(message, c.text, 14, Modifier.weight(1f), FontWeight.SemiBold)
        PopText(
            "Thử lại",
            c.accent,
            14,
            Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onRetry)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            FontWeight.ExtraBold
        )
    }
}

@Composable
private fun HomeHint(text: String, c: PopPalette) {
    PopText(text, c.textSoft, 14)
}

@Composable
private fun HeroStat(modifier: Modifier, value: String, label: String) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // "Giờ bảo vệ" là chuỗi nhập tự do (tối đa 100 ký tự) nên phải căn giữa và giới hạn số dòng.
        PopText(
            value,
            PopInk,
            16,
            weight = FontWeight.Black,
            align = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(3.dp))
        PopText(
            label,
            PopInk.copy(alpha = 0.7f),
            11,
            weight = FontWeight.SemiBold,
            align = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatDivider() {
    Box(
        Modifier
            .width(1.5.dp)
            .height(32.dp)
            .background(PopInk.copy(alpha = 0.2f))
    )
}

@Composable
private fun CountPill(text: String, c: PopPalette) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(c.accent.copy(alpha = 0.18f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        PopText(text, c.accent, 13, weight = FontWeight.ExtraBold, maxLines = 1)
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier,
    c: PopPalette,
    title: String,
    subtitle: String,
    emoji: String,
    badgeColor: Color,
    onClick: () -> Unit
) {
    StickerCard(c, modifier = modifier, onClick = onClick) {
        PopBadge(emoji, badgeColor, c)
        Spacer(Modifier.height(14.dp))
        PopText(title, c.text, 16, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(2.dp))
        PopText(subtitle, c.textSoft, 13)
    }
}

/** Thẻ một hàng: huy hiệu + tiêu đề + mô tả + mũi tên, bấm để mở màn khác. */
@Composable
private fun LinkCard(
    c: PopPalette,
    emoji: String,
    badgeColor: Color,
    title: String,
    subtitle: String,
    clickLabel: String,
    onClick: () -> Unit,
    fill: Color = c.field
) {
    StickerCard(c, fill = fill, onClick = onClick, onClickLabel = clickLabel) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PopBadge(emoji, badgeColor, c)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                PopText(title, c.text, 16, weight = FontWeight.ExtraBold)
                Spacer(Modifier.height(2.dp))
                PopText(subtitle, c.textSoft, 13)
            }
            Spacer(Modifier.width(8.dp))
            PopText("→", c.accent, 20, weight = FontWeight.Black)
        }
    }
}

private data class GettingStartedStep(
    val title: String,
    val hint: String,
    val done: Boolean,
    val onClick: () -> Unit
)

/** Nút "?" trong phần đầu màn Hôm nay: mở lại hướng dẫn sử dụng bất cứ lúc nào. */
@Composable
private fun HelpButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClickLabel = "Mở hướng dẫn sử dụng", role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Hướng dẫn sử dụng" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.dp, PopInk, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            PopText("?", PopInk, 18, weight = FontWeight.Black)
        }
    }
}

@Composable
private fun GettingStartedCard(
    steps: List<GettingStartedStep>,
    onDismiss: () -> Unit,
    c: PopPalette
) {
    val doneCount = steps.count { it.done }
    StickerCard(c, contentPadding = 18.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                PopText("🚀  Bắt đầu với POP AI", c.text, 18, weight = FontWeight.Black)
                Spacer(Modifier.height(2.dp))
                PopText(
                    "Làm lần lượt vài việc nhỏ để POP AI hiểu và hỗ trợ bạn tốt hơn · $doneCount/${steps.size}",
                    c.textSoft,
                    13
                )
            }
            PopText(
                "Ẩn",
                c.accent,
                14,
                Modifier
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Ẩn phần bắt đầu", role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(12.dp))
        // Thanh tiến độ chia đoạn (giống thanh độ mạnh mật khẩu ở màn đăng ký).
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { index, _ ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(if (index < doneCount) PopMint else c.fieldLine)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        steps.forEachIndexed { index, step ->
            if (index > 0) Spacer(Modifier.height(8.dp))
            GettingStartedRow(number = index + 1, step = step, c = c)
        }
    }
}

@Composable
private fun GettingStartedRow(number: Int, step: GettingStartedStep, c: PopPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = step.onClick)
            .background(if (step.done) Color.Transparent else PopAzure.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(if (step.done) PopMint else PopAzure)
                .border(2.dp, c.outline, CircleShape)
                .then(if (step.done) Modifier.semantics { contentDescription = "Đã xong" } else Modifier),
            contentAlignment = Alignment.Center
        ) {
            if (step.done) {
                PopText("✓", PopInk, 15, weight = FontWeight.Black)
            } else {
                PopText("$number", Color.White, 14, weight = FontWeight.Black)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            PopText(
                step.title,
                if (step.done) c.textSoft else c.text,
                15,
                weight = FontWeight.ExtraBold
            )
            PopText(step.hint, c.textSoft, 13)
        }
        if (!step.done) {
            Spacer(Modifier.width(8.dp))
            PopText("→", c.accent, 18, weight = FontWeight.Black)
        }
    }
}

package com.health.pop.ui.focus

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.health.pop.core.focussound.FocusSoundPrefs
import com.health.pop.core.focussound.FocusSoundService
import com.health.pop.domain.model.FocusSessionState
import com.health.pop.domain.usecase.FocusStreakRules
import com.health.pop.ui.common.PopAzure
import com.health.pop.ui.common.PopBadge
import com.health.pop.ui.common.PopButton
import com.health.pop.ui.common.PopErrorBanner
import com.health.pop.ui.common.PopField
import com.health.pop.ui.common.PopInk
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

private const val TIP_FOCUS = "focus"

/**
 * Màn "Đồng hồ tập trung" theo cùng phong cách với màn Đăng nhập / Đăng ký, Hôm nay và Kế hoạch:
 * nền xanh + Pop + bong bóng thoại ở phần đầu, bên dưới là các thẻ sticker (viền đậm, bóng cứng).
 * Màn này tự vẽ nền xanh dưới status bar (AppNavHost chỉ chừa chỗ cho thanh điều hướng dưới).
 */
@Composable
fun FocusTimerScreen(
    viewModel: FocusViewModel
) {
    val context = LocalContext.current
    val overlayPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            viewModel.startTimer()
            FocusOverlayService.prepare(context)
        }
    }
    val soundPrefs = remember { FocusSoundPrefs(context) }
    var soundEnabled by remember { mutableStateOf(soundPrefs.enabled) }
    val startFocusSession = {
        if (Settings.canDrawOverlays(context)) {
            viewModel.startTimer()
            FocusOverlayService.prepare(context)
        } else {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
            overlayPermissionLauncher.launch(settingsIntent)
        }
    }
    val session by viewModel.currentSession.collectAsState()
    val remainingSeconds by viewModel.remainingSeconds.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val focusStreakDays by viewModel.focusStreakDays.collectAsState()

    var interruptionReasonText by remember { mutableStateOf("") }

    val c = rememberPopPalette()
    val onboarding = LocalOnboarding.current
    val showTip = onboarding != null &&
        !onboarding.isTipDismissed(TIP_FOCUS) &&
        !onboarding.isTourVisible

    // ── tâm trạng + lời thoại của Pop ──
    val mood = if (syncMessage != null) PopMood.Oops else PopMood.Happy
    val bubble = when {
        syncMessage != null -> "Chưa đồng bộ được. Xem thông báo bên dưới nhé."
        session.state == FocusSessionState.RUNNING -> "Đang tập trung… cố lên nhé! 💪"
        session.state == FocusSessionState.PAUSED -> "Nghỉ một chút, nhớ quay lại nhé ☕"
        session.state == FocusSessionState.COMPLETED -> "Làm tốt lắm! Chấm sao cho phiên này nhé 🌟"
        else -> "Sẵn sàng tập trung chưa? Mình đợi bạn nè ⏱️"
    }

    val totalSecs = (session.targetMinutes * 60).coerceAtLeast(1)
    val progress = (remainingSeconds.toFloat() / totalSecs.toFloat()).coerceIn(0f, 1f)
    val running = session.state == FocusSessionState.RUNNING
    val paused = session.state == FocusSessionState.PAUSED
    val completed = session.state == FocusSessionState.COMPLETED

    PopPage(
        c = c,
        label = "POP AI  ·  TẬP TRUNG",
        title = "Đồng hồ tập trung",
        bubble = bubble,
        mood = mood
    ) {
        if (showTip) {
            FocusTip(c = c, onDismiss = { onboarding?.dismissTip(TIP_FOCUS) })
        }

        // Nhiệm vụ đang chọn
        StickerCard(c, contentPadding = 16.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PopBadge("📚", PopAzure, c, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    PopText("Nhiệm vụ hiện tại", c.textSoft, 12, weight = FontWeight.Bold)
                    PopText(
                        session.taskName.ifEmpty { "Nhiệm vụ học tập" },
                        c.text,
                        16,
                        weight = FontWeight.ExtraBold
                    )
                }
            }
        }

        FocusStreakCard(c = c, streakDays = focusStreakDays)

        // Vòng đếm ngược
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            FocusRing(
                progress = progress,
                c = c,
                arcColor = if (completed) PopMint else PopSun,
                time = viewModel.formatUseCase.formatTimerDisplay(remainingSeconds),
                status = when (session.state) {
                    FocusSessionState.NOT_STARTED -> "Sẵn sàng"
                    FocusSessionState.RUNNING -> "Đang tập trung..."
                    FocusSessionState.PAUSED -> "Tạm dừng"
                    FocusSessionState.COMPLETED -> "Hoàn thành phiên!"
                }
            )
        }

        if (running) {
            PopText(
                "Chế độ tập trung đang bật: đã khoá chuyển màn hình. Bấm “Tạm dừng” nếu cần rời đi.",
                c.textSoft,
                13,
                Modifier.fillMaxWidth(),
                align = TextAlign.Center
            )
        }

        if (session.state == FocusSessionState.NOT_STARTED) {
            FocusDurationCard(
                c = c,
                targetMinutes = session.targetMinutes,
                onTargetChange = viewModel::setTargetMinutes
            )
        } else if (!completed) {
            PopText(
                streakProgressHint(session.targetMinutes),
                c.textSoft,
                13,
                Modifier.fillMaxWidth(),
                align = TextAlign.Center
            )
        }

        if (!completed) {
            FocusSoundCard(
                c = c,
                enabled = soundEnabled,
                onToggle = { on ->
                    soundEnabled = on
                    soundPrefs.enabled = on
                    if (running) {
                        if (on) FocusSoundService.startIfEnabled(context) else FocusSoundService.stop(context)
                    }
                }
            )
        }

        // Nút điều khiển
        if (!completed) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (running) {
                    PopButton(
                        text = "⏸  Tạm dừng",
                        c = c,
                        onClick = { viewModel.pauseTimer() },
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    PopButton(
                        text = if (paused) "▶  Tiếp tục" else "▶  Bắt đầu",
                        c = c,
                        onClick = { startFocusSession() },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (running || paused) {
                    PopButton(
                        text = "✅  Hoàn thành",
                        c = c,
                        onClick = { viewModel.completeSession() },
                        modifier = Modifier.weight(1f),
                        fill = PopMint
                    )
                }
            }
        }

        // Ghi nhận gián đoạn
        if (running || paused) {
            StickerCard(c, contentPadding = 18.dp) {
                FocusSectionHeader(
                    emoji = "🔔",
                    color = PopPink,
                    title = "Ghi nhận gián đoạn (${session.interruptionCount} lần)",
                    c = c
                )
                Spacer(Modifier.height(12.dp))
                val record = {
                    viewModel.recordInterruption(interruptionReasonText)
                    interruptionReasonText = ""
                }
                PopField(
                    value = interruptionReasonText,
                    onValueChange = { interruptionReasonText = it },
                    hint = "Lý do (Ví dụ: Tin nhắn, Tiếng ồn)",
                    c = c,
                    badge = "💬",
                    badgeColor = PopSun,
                    imeAction = ImeAction.Done,
                    onImeAction = { record() },
                    trailing = {
                        Box(
                            modifier = Modifier
                                .heightIn(min = 44.dp)
                                .clip(CircleShape)
                                .background(PopSun)
                                .border(2.dp, c.outline, CircleShape)
                                .clickable(
                                    onClickLabel = "Ghi lại gián đoạn",
                                    role = Role.Button,
                                    onClick = { record() }
                                )
                                .padding(horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            PopText("Ghi lại", PopInk, 14, weight = FontWeight.ExtraBold, maxLines = 1)
                        }
                    }
                )
            }
        }

        // Đánh giá sau phiên
        if (completed) {
            StickerCard(c, contentPadding = 18.dp) {
                FocusSectionHeader(
                    emoji = "🌟",
                    color = PopSun,
                    title = "Đánh giá mức độ tập trung phiên này",
                    c = c
                )
                Spacer(Modifier.height(8.dp))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    (1..5).forEach { star ->
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .clickable(
                                    onClickLabel = "Chấm $star sao",
                                    role = Role.Button,
                                    onClick = { viewModel.setSelfRating(star) }
                                )
                                .semantics { contentDescription = "$star sao" },
                            contentAlignment = Alignment.Center
                        ) {
                            PopText(
                                "⭐",
                                c.text,
                                30,
                                Modifier.alpha(if (star <= session.selfRating) 1f else 0.25f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                PopText(
                    "Chúc mừng bạn đã hoàn thành phiên tập trung!",
                    c.accent,
                    15,
                    weight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(8.dp))
                if (FocusStreakRules.earnsStreak(session.actualMinutes)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PopBadge("🔥", PopSun, c, size = 34.dp)
                        Spacer(Modifier.width(10.dp))
                        PopText(
                            if (focusStreakDays != null) {
                                "Đủ 1 giờ tập trung! Streak hiện tại: $focusStreakDays ngày."
                            } else {
                                "Đủ 1 giờ tập trung! Streak sẽ được cập nhật khi đồng bộ được với máy chủ."
                            },
                            c.text,
                            14,
                            Modifier.weight(1f),
                            FontWeight.ExtraBold
                        )
                    }
                } else {
                    PopText(
                        "Phiên này ${session.actualMinutes} phút. Tập trung đủ ${FocusStreakRules.STREAK_MINUTES} phút trong một phiên để nhận streak.",
                        c.textSoft,
                        13
                    )
                }
                Spacer(Modifier.height(16.dp))
                PopButton(
                    text = "Bắt đầu phiên mới",
                    c = c,
                    onClick = { viewModel.startNewSession() }
                )
            }
        }

        syncMessage?.let { PopErrorBanner(it, c) }
    }
}

private fun streakProgressHint(targetMinutes: Int): String =
    if (FocusStreakRules.earnsStreak(targetMinutes)) {
        "Phiên ${targetMinutes} phút: tập trung đủ ${FocusStreakRules.STREAK_MINUTES} phút để nhận streak."
    } else {
        "Phiên dưới ${FocusStreakRules.STREAK_MINUTES} phút không tính streak."
    }

/** Gợi ý "màn này dùng để làm gì": hiện một lần cho mỗi tài khoản đến khi bấm "Đã hiểu". */
@Composable
private fun FocusTip(c: PopPalette, onDismiss: () -> Unit) {
    StickerCard(
        c,
        fill = PopSun.copy(alpha = 0.25f).compositeOver(c.card),
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PopText("💡", c.text, 18)
            Spacer(Modifier.width(10.dp))
            Column {
                PopText("Cách dùng đồng hồ tập trung", c.text, 15, weight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                PopText(
                    "Bấm “Bắt đầu” để chạy một phiên cho việc đang chọn. Khi đang chạy, nếu bị xao nhãng hãy ghi lại lý do ở thẻ “Ghi nhận gián đoạn”. Khi đồng hồ chạy, ứng dụng sẽ khoá chuyển màn hình và nút Back; rời ứng dụng sẽ có cảnh báo nhắc bạn quay lại. Muốn rời đi hãy bấm “Tạm dừng”. Xong thì bấm “Hoàn thành” và chấm sao cho mức tập trung của bạn. Trước khi bắt đầu bạn có thể chỉnh thời gian tập trung; tập trung đủ 1 giờ trong một phiên sẽ được tính 1 streak.",
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
private fun FocusSectionHeader(
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

/** Vòng đếm ngược kiểu sticker: đĩa viền đậm + bóng cứng, rãnh có viền, cung tiến độ vàng bo tròn. */
@Composable
private fun FocusRing(
    progress: Float,
    c: PopPalette,
    arcColor: Color,
    time: String,
    status: String
) {
    val animated by animateFloatAsState(progress, tween(900, easing = LinearEasing), label = "focusProgress")

    Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val shadow = 5.dp.toPx()
            val border = 2.dp.toPx()
            val stroke = 24.dp.toPx()
            val outerR = (size.minDimension - shadow) / 2f
            val center = Offset(size.width / 2f, outerR)

            // đĩa nền + bóng cứng
            drawCircle(c.outline, outerR, center + Offset(0f, shadow))
            drawCircle(c.field, outerR, center)
            drawCircle(c.outline, outerR, center, style = Stroke(border))

            // rãnh có viền hai mép
            val ringR = outerR - 28.dp.toPx()
            drawCircle(c.card, ringR, center, style = Stroke(stroke))
            drawCircle(c.outline, ringR + stroke / 2f, center, style = Stroke(border))
            drawCircle(c.outline, ringR - stroke / 2f, center, style = Stroke(border))

            // cung tiến độ
            val sweep = 360f * animated
            if (sweep > 0.5f) {
                drawArc(
                    color = arcColor,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = center - Offset(ringR, ringR),
                    size = Size(ringR * 2f, ringR * 2f),
                    style = Stroke(stroke - border * 2f - 2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PopText(time, c.text, 48, weight = FontWeight.Black, align = TextAlign.Center, maxLines = 1)
            Spacer(Modifier.height(2.dp))
            PopText(status, c.accent, 15, weight = FontWeight.ExtraBold, align = TextAlign.Center)
        }
    }
}

@Composable
private fun FocusStreakCard(c: PopPalette, streakDays: Int?) {
    val active = (streakDays ?: 0) > 0
    StickerCard(c, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.alpha(if (active) 1f else 0.55f)) {
                PopBadge("🔥", PopSun, c, size = 44.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                PopText(
                    if (streakDays != null) "Streak tập trung: $streakDays ngày" else "Streak tập trung: —",
                    c.text,
                    16,
                    weight = FontWeight.ExtraBold
                )
                Spacer(Modifier.height(2.dp))
                PopText(
                    "Hoàn thành một phiên đủ ${FocusStreakRules.STREAK_MINUTES} phút mỗi ngày để giữ streak.",
                    c.textSoft,
                    13
                )
            }
        }
    }
}

@Composable
private fun FocusDurationCard(c: PopPalette, targetMinutes: Int, onTargetChange: (Int) -> Unit) {
    val sliderRange = FocusStreakRules.MIN_TARGET_MINUTES.toFloat()..FocusStreakRules.MAX_TARGET_MINUTES.toFloat()
    val sliderSteps = (FocusStreakRules.MAX_TARGET_MINUTES - FocusStreakRules.MIN_TARGET_MINUTES) /
        FocusStreakRules.STEP_MINUTES - 1
    StickerCard(c, contentPadding = 18.dp) {
        FocusSectionHeader("⏲️", PopSun, "Thời gian tập trung", c)
        Spacer(Modifier.height(8.dp))
        PopText("$targetMinutes phút", c.accent, 26, weight = FontWeight.Black)
        Slider(
            value = targetMinutes.toFloat().coerceIn(sliderRange),
            onValueChange = { onTargetChange(it.toInt()) },
            valueRange = sliderRange,
            steps = sliderSteps,
            colors = SliderDefaults.colors(
                thumbColor = PopAzure,
                activeTrackColor = PopAzure,
                inactiveTrackColor = c.fieldLine,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent
            )
        )
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusStreakRules.PRESET_MINUTES.forEach { preset ->
                val isSelected = targetMinutes == preset
                val shape = RoundedCornerShape(14.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .clip(shape)
                        .background(if (isSelected) PopAzure else c.field)
                        .border(2.dp, if (isSelected) c.outline else c.fieldLine, shape)
                        .selectable(
                            selected = isSelected,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onTargetChange(preset) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    PopText(
                        "$preset",
                        if (isSelected) Color.White else c.text,
                        20,
                        weight = FontWeight.Black,
                        align = TextAlign.Center
                    )
                    PopText(
                        if (preset == FocusStreakRules.STREAK_MINUTES) "phút 🔥" else "phút",
                        if (isSelected) Color.White.copy(alpha = 0.85f) else c.textSoft,
                        11,
                        weight = FontWeight.SemiBold,
                        align = TextAlign.Center
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PopText(
            streakProgressHint(targetMinutes),
            if (FocusStreakRules.earnsStreak(targetMinutes)) c.accent else c.textSoft,
            13,
            weight = if (FocusStreakRules.earnsStreak(targetMinutes)) FontWeight.Bold else FontWeight.Medium
        )
    }
}

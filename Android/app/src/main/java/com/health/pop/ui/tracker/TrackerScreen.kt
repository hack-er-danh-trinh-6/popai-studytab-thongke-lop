package com.health.pop.ui.tracker

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.health.pop.core.tracker.BackgroundAccess
import com.health.pop.core.tracker.UsageNotificationHelper
import com.health.pop.core.tracker.UsageStatsReader
import com.health.pop.ui.common.PopBadge
import com.health.pop.ui.common.PopButton
import com.health.pop.ui.common.PopCoral
import com.health.pop.ui.common.PopErrorBanner
import com.health.pop.ui.common.PopField
import com.health.pop.ui.common.PopInk
import com.health.pop.ui.common.PopMint
import com.health.pop.ui.common.PopMood
import com.health.pop.ui.common.PopPage
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopPillButton
import com.health.pop.ui.common.PopSun
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.StickerCard
import com.health.pop.ui.common.rememberPopPalette
import kotlinx.coroutines.delay

private val PRESET_MINUTES = listOf(60, 90, 120, 180, 240)

@Composable
fun TrackerScreen(viewModel: TrackerViewModel) {
    val c = rememberPopPalette()
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Việc đọc lại trạng thái khi quay về từ màn cấp quyền do AppNavHost gọi (OnResumeEffect → viewModel.refresh()).
    // Cập nhật số phút đang chạy khi màn hình đang mở.
    LaunchedEffect(state.enabled, state.hasUsageAccess) {
        while (state.enabled && state.hasUsageAccess) {
            delay(15_000)
            viewModel.refresh()
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.refresh()
    }

    val anyOver = state.apps.any { it.isOver }
    PopPage(
        c = c,
        label = "THEO DÕI",
        title = "Thời gian dùng app",
        bubble = when {
            anyOver -> "Bạn đã vượt hạn rồi. Nghỉ mắt một chút nhé!"
            state.enabled -> "Mình đang để ý giúp bạn."
            else -> "Đặt hạn mức, mình sẽ nhắc bạn nghỉ."
        },
        mood = if (anyOver) PopMood.Oops else PopMood.Happy
    ) {
        StickerCard(c, fill = if (state.enabled) PopMint.copy(alpha = 0.22f).compositeOver(c.card) else c.field) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PopBadge("⏱️", PopMint, c)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    PopText("Tự động theo dõi", c.text, 16, weight = FontWeight.ExtraBold)
                    Spacer(Modifier.height(2.dp))
                    PopText(
                        if (state.enabled) "Đang bật" else "Đang tắt",
                        c.textSoft, 13
                    )
                }
                PopPillButton(
                    text = if (state.enabled) "Tắt" else "Bật",
                    c = c,
                    fill = if (state.enabled) c.field else PopSun,
                    onClick = {
                        val next = !state.enabled
                        viewModel.setEnabled(next)
                        if (next && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                )
            }
        }

        if (state.enabled && !state.hasUsageAccess) {
            PopErrorBanner(
                message = "Cần quyền \"Truy cập dữ liệu sử dụng\" để đọc thời gian dùng TikTok, YouTube.",
                c = c,
                actionLabel = "Cấp quyền",
                onAction = { context.startActivity(UsageStatsReader.usageAccessSettingsIntent()) }
            )
        }
        if (state.enabled && !state.notificationsAllowed) {
            PopErrorBanner(
                message = "Thông báo đang bị tắt nên mình không nhắc bạn được.",
                c = c,
                actionLabel = "Mở cài đặt",
                onAction = {
                    context.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            )
        }

        state.apps.forEach { item ->
            AppLimitCard(c, item, onSave = { viewModel.setLimit(item.app, it) })
        }

        StickerCard(c) {
            PopText("Giữ theo dõi chạy nền", c.text, 16, weight = FontWeight.ExtraBold)
            Spacer(Modifier.height(2.dp))
            PopText("Để hệ thống không tự tắt tracker khi bạn khoá máy hoặc xem video.", c.textSoft, 13)
            Spacer(Modifier.height(12.dp))
            PermissionRow(
                c, "🔋", "Chạy nền không giới hạn",
                "Bỏ tối ưu pin cho POP AI",
                done = state.batteryUnrestricted,
                actionLabel = "Cho phép",
                onClick = { BackgroundAccess.requestIgnoreBatteryOptimizations(context) }
            )
            Spacer(Modifier.height(10.dp))
            PermissionRow(
                c, "🚀", "Tự khởi động",
                "Bật mục Tự khởi động cho POP AI (tuỳ hãng máy, không kiểm tra được tự động)",
                done = false,
                actionLabel = "Mở cài đặt",
                onClick = { BackgroundAccess.openAutoStartSettings(context) }
            )
            Spacer(Modifier.height(10.dp))
            PermissionRow(
                c, "🛡️", "Quản trị viên thiết bị",
                "Chống gỡ nhầm POP AI; muốn gỡ phải tắt quyền này trước",
                done = state.deviceAdminActive,
                actionLabel = "Kích hoạt",
                onClick = { BackgroundAccess.requestDeviceAdmin(context) },
                secondaryLabel = "Tắt",
                onSecondary = {
                    BackgroundAccess.removeDeviceAdmin(context)
                    viewModel.refresh()
                }
            )
        }

        PopText(
            "Thời gian tính từ 0h hôm nay. Bạn sẽ nhận thông báo khi dùng đến 80% hạn mức và khi vượt hạn; " +
                "nếu vẫn đang xem, mình nhắc lại sau mỗi 15 phút. Dữ liệu chỉ nằm trên máy của bạn.",
            c.textSoft, 12
        )
    }
}

@Composable
private fun AppLimitCard(c: PopPalette, item: AppUsageUi, onSave: (Int) -> Unit) {
    // Đặt lại ô nhập mỗi khi hạn mức đã lưu thay đổi.
    var input by remember(item.limitMinutes) {
        mutableStateOf(if (item.limitMinutes > 0) item.limitMinutes.toString() else "")
    }
    val parsed = input.toIntOrNull()
    val inputError = when {
        input.isBlank() -> null
        parsed == null || parsed < 0 -> "Nhập số phút hợp lệ"
        parsed > 24 * 60 -> "Tối đa 1440 phút (24 giờ)"
        else -> null
    }
    val dirty = (parsed ?: 0) != item.limitMinutes

    StickerCard(c, fill = if (item.isOver) PopCoral.copy(alpha = 0.14f).compositeOver(c.card) else c.field) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PopBadge(item.app.emoji, if (item.isOver) PopCoral else PopSun, c)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                PopText(item.app.displayName, c.text, 16, weight = FontWeight.ExtraBold)
                Spacer(Modifier.height(2.dp))
                PopText(
                    if (!item.installed) "Chưa cài trên máy này"
                    else "Hôm nay: ${UsageNotificationHelper.formatMinutes(item.usedMinutes)}" +
                        if (item.hasLimit) " / ${UsageNotificationHelper.formatMinutes(item.limitMinutes.toLong())}" else "",
                    c.textSoft, 13
                )
            }
            if (item.isOver) PopText("Vượt hạn", PopCoral, 13, weight = FontWeight.ExtraBold)
        }

        if (item.hasLimit) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(c.fieldLine)
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(item.progress)
                        .height(10.dp)
                        .clip(CircleShape)
                        .background(if (item.isOver) PopCoral else PopMint)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        PopField(
            value = input,
            onValueChange = { input = it.filter(Char::isDigit).take(4) },
            hint = "Hạn mức mỗi ngày (phút)",
            c = c,
            label = "Hạn dùng ${item.app.displayName}",
            error = inputError,
            keyboardType = KeyboardType.Number,
            trailing = { PopText("phút", c.textSoft, 13) }
        )
        if (parsed != null && parsed > 0 && inputError == null) {
            Spacer(Modifier.height(6.dp))
            PopText("= ${UsageNotificationHelper.formatMinutes(parsed.toLong())} mỗi ngày", c.textSoft, 12)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PRESET_MINUTES.forEach { preset ->
                PopText(
                    UsageNotificationHelper.formatMinutes(preset.toLong()),
                    c.text, 12, weight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(c.card)
                        .clickable(role = Role.Button) { input = preset.toString() }
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            PopPillButton(
                text = "Lưu hạn mức",
                c = c,
                enabled = inputError == null && (dirty || input.isBlank() && item.hasLimit),
                onClick = { onSave(parsed ?: 0) }
            )
            if (item.hasLimit) {
                PopPillButton(text = "Bỏ hạn mức", c = c, fill = c.card, onClick = { onSave(0) })
            }
        }
    }
}

@Composable
private fun PermissionRow(
    c: PopPalette,
    emoji: String,
    title: String,
    description: String,
    done: Boolean,
    actionLabel: String,
    onClick: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PopBadge(emoji, if (done) PopMint else PopSun, c, size = 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            PopText(title + if (done) " ✓" else "", c.text, 14, weight = FontWeight.ExtraBold)
            PopText(description, c.textSoft, 12)
        }
        Spacer(Modifier.width(8.dp))
        if (done && secondaryLabel != null && onSecondary != null) {
            PopPillButton(text = secondaryLabel, c = c, fill = c.card, onClick = onSecondary)
        } else if (!done) {
            PopPillButton(text = actionLabel, c = c, onClick = onClick)
        }
    }
}

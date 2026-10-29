package com.health.pop.ui.classroom

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.health.pop.domain.model.ClassNotification
import com.health.pop.domain.model.ClassOption
import com.health.pop.domain.model.MembershipStatus
import com.health.pop.domain.model.School
import com.health.pop.ui.common.PopAzure
import com.health.pop.ui.common.PopBadge
import com.health.pop.ui.common.PopButton
import com.health.pop.ui.common.PopCoral
import com.health.pop.ui.common.PopErrorBanner
import com.health.pop.ui.common.PopField
import com.health.pop.ui.common.PopLoadingDots
import com.health.pop.ui.common.PopMint
import com.health.pop.ui.common.PopMood
import com.health.pop.ui.common.PopPage
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopPillButton
import com.health.pop.ui.common.PopPink
import com.health.pop.ui.common.PopSun
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.StickerCard
import com.health.pop.ui.common.rememberPopPalette
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private const val PENDING_REFRESH_MS = 15_000L

private const val DATA_SHARING_NOTE =
    "Sau khi giáo viên duyệt, giáo viên xem được số phút tập trung, số phiên hoàn thành và số lần bị ngắt " +
        "của bạn (từ lúc được duyệt). Giáo viên không xem được nhật ký, bài tự đánh giá hay cuộc trò chuyện với POP-AI. " +
        "Bạn cùng lớp chỉ thấy tên của bạn trong danh sách thành viên. Bạn có thể rời lớp bất cứ lúc nào."

/**
 * Màn "Lớp học" theo cùng phong cách với màn Đăng nhập / Đăng ký và các tab chính.
 * Màn này tự vẽ nền xanh dưới status bar (AppNavHost chỉ chừa chỗ cho thanh điều hướng dưới).
 */
@Composable
fun ClassroomScreen(
    viewModel: ClassroomViewModel,
    isOnboarding: Boolean,
    onClose: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val status = state.membership.status
    var pendingClass by remember { mutableStateOf<ClassOption?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    val c = rememberPopPalette()

    // Khi đang chờ duyệt, tự làm mới để học sinh thấy ngay khi giáo viên duyệt.
    LaunchedEffect(status) {
        if (status == MembershipStatus.PENDING) {
            while (true) {
                delay(PENDING_REFRESH_MS)
                viewModel.refresh(silent = true)
            }
        }
    }

    // ── tâm trạng + lời thoại của Pop ──
    val mood = if (state.errorMessage != null || status == MembershipStatus.REJECTED) PopMood.Oops else PopMood.Happy
    val bubble = when {
        state.errorMessage != null -> "Chưa tải được thông tin lớp. Xem thông báo bên dưới nhé."
        status == MembershipStatus.APPROVED -> "Bạn đang ở lớp ${state.membership.className} 🎒"
        status == MembershipStatus.PENDING -> "Đang chờ thầy cô duyệt, kiên nhẫn nhé ⏳"
        status == MembershipStatus.REJECTED -> "Yêu cầu chưa được duyệt. Thử lớp khác nhé 🙂"
        isOnboarding -> "Chọn trường và lớp của bạn nhé 🏫"
        else -> "Vào lớp để thầy cô đồng hành cùng bạn 🏫"
    }

    PopPage(
        c = c,
        label = "POP AI  ·  LỚP HỌC",
        title = "Lớp học",
        bubble = bubble,
        mood = mood,
        heroActions = {
            HeroAction(
                text = if (isOnboarding && status != MembershipStatus.APPROVED) "Để sau" else "Xong",
                onClick = onClose
            )
        }
    ) {
        if (state.isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PopLoadingDots(c.accent) }
        }

        state.errorMessage?.let { message -> PopErrorBanner(message, c) }

        if (isOnboarding && state.membershipLoaded && status == MembershipStatus.NONE) {
            PopText(
                "Chọn trường và lớp của bạn để gửi yêu cầu cho giáo viên. Bạn vẫn dùng đầy đủ POP AI nếu bỏ qua bước này.",
                c.textSoft,
                14
            )
        }

        when (status) {
            MembershipStatus.APPROVED -> {
                StickerCard(c, contentPadding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PopBadge("🎒", PopMint, c, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            PopText("Bạn đang ở lớp ${state.membership.className}", c.text, 17, weight = FontWeight.ExtraBold)
                            PopText(
                                "${state.membership.schoolName} · GV ${state.membership.teacherName}",
                                c.textSoft,
                                13
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    PopText(DATA_SHARING_NOTE, c.textSoft, 13)
                    Spacer(Modifier.height(16.dp))
                    PopButton(
                        text = "Rời lớp",
                        c = c,
                        loading = state.isSubmitting,
                        fill = PopCoral,
                        onClick = { confirmLeave = true }
                    )
                }
                InboxSection(items = state.inbox?.items.orEmpty(), c = c)
            }

            MembershipStatus.PENDING -> {
                StickerCard(c, contentPadding = 18.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PopBadge("⏳", PopSun, c, size = 44.dp)
                        Spacer(Modifier.width(12.dp))
                        PopText("Đang chờ giáo viên duyệt", c.text, 17, Modifier.weight(1f), FontWeight.ExtraBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    PopText(
                        "Bạn đã gửi yêu cầu vào lớp ${state.membership.className} · ${state.membership.schoolName}. " +
                            "Khi được duyệt, trạng thái sẽ tự cập nhật ở đây.",
                        c.textSoft,
                        14
                    )
                    Spacer(Modifier.height(16.dp))
                    PopButton(
                        text = "Huỷ yêu cầu",
                        c = c,
                        loading = state.isSubmitting,
                        fill = PopCoral,
                        onClick = viewModel::leaveClass
                    )
                }
            }

            else -> {
                if (!state.membershipLoaded) {
                    // Chưa biết học sinh đã vào lớp hay chưa (đang tải hoặc tải lỗi): không hiện bộ chọn trường
                    // với dòng "chưa có trường nào" dễ gây hiểu nhầm; lỗi đã hiện ở banner phía trên.
                    if (!state.isLoading) {
                        PopButton(text = "Thử lại", c = c, onClick = viewModel::retry)
                    }
                } else {
                    if (status == MembershipStatus.REJECTED) {
                        StickerCard(c, contentPadding = 16.dp) {
                            Row(verticalAlignment = Alignment.Top) {
                                PopBadge("😕", PopCoral, c, size = 38.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    PopText("Yêu cầu chưa được duyệt", c.text, 16, weight = FontWeight.ExtraBold)
                                    Spacer(Modifier.height(4.dp))
                                    PopText(
                                        "Giáo viên chưa duyệt yêu cầu vào lớp ${state.membership.className} · ${state.membership.schoolName}. " +
                                            "Bạn có thể kiểm tra lại tên lớp và gửi yêu cầu khác.",
                                        c.textSoft,
                                        14
                                    )
                                }
                            }
                        }
                    }
                    ClassPicker(
                        state = state,
                        c = c,
                        onQueryChange = viewModel::onSchoolQueryChange,
                        onSelectSchool = viewModel::selectSchool,
                        onClearSchool = viewModel::clearSchool,
                        onChooseClass = { pendingClass = it },
                        onRetrySchools = viewModel::retrySchools,
                        onRetryClasses = viewModel::retryClasses
                    )
                }
            }
        }
    }

    pendingClass?.let { option ->
        PopDialog(
            c = c,
            title = "Gửi yêu cầu vào lớp ${option.name}?",
            message = "${state.selectedSchool?.name.orEmpty()} · GV ${option.teacherName}\n\n$DATA_SHARING_NOTE",
            confirmText = "Gửi yêu cầu",
            dismissText = "Huỷ",
            onConfirm = { viewModel.requestJoin(option); pendingClass = null },
            onDismiss = { pendingClass = null }
        )
    }

    if (confirmLeave) {
        PopDialog(
            c = c,
            title = "Rời lớp ${state.membership.className}?",
            message = "Giáo viên sẽ không còn xem được dữ liệu tập trung mới của bạn và bạn không nhận thông báo của lớp nữa.",
            confirmText = "Rời lớp",
            dismissText = "Ở lại",
            confirmFill = PopCoral,
            onConfirm = { viewModel.leaveClass(); confirmLeave = false },
            onDismiss = { confirmLeave = false }
        )
    }
}

/** Nút nhỏ trong phần đầu (nền xanh): viền trắng, chữ trắng. */
@Composable
private fun RowScope.HeroAction(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.18f))
            .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center
    ) {
        PopText(text, Color.White, 14, weight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** Hộp thoại kiểu sticker: nền thẻ, bo góc lớn, nút viên thuốc. */
@Composable
private fun PopDialog(
    c: PopPalette,
    title: String,
    message: String,
    confirmText: String,
    dismissText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmFill: Color = PopSun
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        shape = RoundedCornerShape(28.dp),
        title = { PopText(title, c.text, 18, weight = FontWeight.Black) },
        text = { PopText(message, c.textSoft, 14) },
        confirmButton = { PopPillButton(confirmText, c, onConfirm, fill = confirmFill) },
        dismissButton = {
            PopText(
                dismissText,
                c.accent,
                14,
                Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onDismiss)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                FontWeight.ExtraBold
            )
        }
    )
}

@Composable
private fun ClassPicker(
    state: ClassroomUiState,
    c: PopPalette,
    onQueryChange: (String) -> Unit,
    onSelectSchool: (School) -> Unit,
    onClearSchool: () -> Unit,
    onChooseClass: (ClassOption) -> Unit,
    onRetrySchools: () -> Unit,
    onRetryClasses: () -> Unit
) {
    val school = state.selectedSchool
    val schoolsError = state.schoolsError
    val classesError = state.classesError
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (school == null) {
            PopText("Chọn trường", c.text, 18, weight = FontWeight.Black)
            PopField(
                value = state.schoolQuery,
                onValueChange = onQueryChange,
                hint = "Tìm tên trường",
                c = c,
                badge = "🔍",
                badgeColor = PopAzure
            )
            if (state.isLoadingSchools && state.schools.isEmpty()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PopLoadingDots(c.accent) }
            } else if (schoolsError != null) {
                PopErrorBanner(schoolsError, c, actionLabel = "Thử lại", onAction = onRetrySchools)
            } else if (state.schools.isEmpty()) {
                PopText(
                    "Chưa tìm thấy trường nào. Hãy nhờ giáo viên khai báo trường và lớp trên trang giáo viên trước.",
                    c.textSoft,
                    14
                )
            } else {
                state.schools.forEach { item ->
                    StickerCard(c, contentPadding = 16.dp, onClick = { onSelectSchool(item) }, onClickLabel = "Chọn trường ${item.name}") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PopBadge("🏫", PopAzure, c, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                PopText(item.name, c.text, 16, weight = FontWeight.ExtraBold)
                                PopText("${item.classCount} lớp", c.textSoft, 13)
                            }
                            PopText("›", c.accent, 26, weight = FontWeight.Black)
                        }
                    }
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PopText("Chọn lớp", c.text, 18, weight = FontWeight.Black)
                    PopText(school.name, c.textSoft, 14)
                }
                PopPillButton("Đổi trường", c, onClearSchool, fill = c.field)
            }
            if (state.isLoadingClasses) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PopLoadingDots(c.accent) }
            } else if (classesError != null) {
                PopErrorBanner(classesError, c, actionLabel = "Thử lại", onAction = onRetryClasses)
            } else if (state.classes.isEmpty()) {
                PopText("Trường này chưa có lớp nào.", c.textSoft, 14)
            } else {
                state.classes.forEach { option ->
                    StickerCard(c, contentPadding = 16.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PopBadge("📖", PopPink, c, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                PopText(option.name, c.text, 16, weight = FontWeight.ExtraBold)
                                PopText("GV ${option.teacherName}", c.textSoft, 13)
                            }
                            Spacer(Modifier.width(12.dp))
                            PopPillButton(
                                text = "Vào lớp",
                                c = c,
                                onClick = { onChooseClass(option) },
                                enabled = !state.isSubmitting
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxSection(items: List<ClassNotification>, c: PopPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PopText("Thông báo từ giáo viên", c.text, 18, weight = FontWeight.Black)
        if (items.isEmpty()) {
            StickerCard(c, contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PopText("📭", c.text, 20)
                    Spacer(Modifier.width(10.dp))
                    PopText("Chưa có thông báo nào.", c.textSoft, 14, Modifier.weight(1f))
                }
            }
            return
        }
        items.forEach { item ->
            StickerCard(c, contentPadding = 16.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PopText(
                        item.title,
                        c.text,
                        16,
                        Modifier.weight(1f),
                        if (item.read) FontWeight.SemiBold else FontWeight.Black
                    )
                    if (!item.read) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .clip(CircleShape)
                                .background(PopPink)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            PopText("Mới", Color.White, 12, weight = FontWeight.ExtraBold)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                PopText(item.body, c.text, 14)
                Spacer(Modifier.height(8.dp))
                val meta = listOf(item.senderName, formatNotificationTime(item.createdAt))
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                PopText(meta, c.textSoft, 12)
            }
        }
    }
}

/** Chuyển thời gian ISO-8601 (UTC) của máy chủ sang giờ địa phương dạng dd/MM HH:mm. */
internal fun formatNotificationTime(iso: String): String {
    if (iso.length < 19) return ""
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date: Date = parser.parse(iso.substring(0, 19)) ?: return ""
        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(date)
    } catch (_: Exception) {
        ""
    }
}

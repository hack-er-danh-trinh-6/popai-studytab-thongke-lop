package com.health.pop.ui.study

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.health.pop.domain.model.ClassMember
import com.health.pop.domain.model.ClassRoster
import com.health.pop.domain.model.MembershipStatus
import com.health.pop.domain.model.TimetableEntry
import com.health.pop.domain.usecase.TimetableRules
import com.health.pop.ui.common.PopAzure
import com.health.pop.ui.common.PopBadge
import com.health.pop.ui.common.PopButton
import com.health.pop.ui.common.PopErrorBanner
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
import java.util.Calendar

private const val COLLAPSED_MEMBER_COUNT = 5

/**
 * Tab "Học tập": lớp đang tham gia, thời khóa biểu, AI Giáo sư bài tập và danh sách thành viên lớp.
 * Màn này tự vẽ nền xanh dưới status bar (AppNavHost chỉ chừa chỗ cho thanh điều hướng dưới).
 */
@Composable
fun StudyScreen(
    viewModel: StudyViewModel,
    isStudent: Boolean,
    onOpenClassroom: () -> Unit,
    onOpenHomeworkAi: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val status = state.membership.status
    val c = rememberPopPalette()

    val bubble = when {
        !isStudent -> "Hỏi Giáo sư POP khi bạn cần hiểu bài nhé 🎓"
        state.errorMessage != null -> "Chưa tải được lớp của bạn. Xem thông báo bên dưới nhé."
        status == MembershipStatus.APPROVED -> "Bạn đang ở lớp ${state.membership.className} 🎒"
        status == MembershipStatus.PENDING -> "Đang chờ thầy cô duyệt, kiên nhẫn nhé ⏳"
        else -> "Vào lớp để học cùng bạn bè 🏫"
    }

    PopPage(
        c = c,
        label = "POP AI  ·  HỌC TẬP",
        title = "Không gian học tập",
        bubble = bubble,
        mood = if (state.errorMessage != null) PopMood.Oops else PopMood.Happy
    ) {
        if (isStudent) {
            if (state.isLoading && !state.membershipLoaded) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { PopLoadingDots(c.accent) }
            }
            state.errorMessage?.let { message ->
                PopErrorBanner(message, c, actionLabel = "Thử lại", onAction = viewModel::refresh)
            }
            if (state.membershipLoaded) {
                ClassCard(state, c, onOpenClassroom)
                if (state.inClass) {
                    TimetableCard(state.timetable, state.timetableError, c, viewModel::refresh)
                }
            }
        }

        HomeworkAiCard(c, onOpenHomeworkAi)

        if (isStudent && state.inClass) {
            MembersCard(state.roster, state.rosterError, c, viewModel::refresh)
        }
    }
}

/* ───────────────────────── Lớp đang tham gia ───────────────────────── */

@Composable
private fun ClassCard(state: StudyUiState, c: PopPalette, onOpenClassroom: () -> Unit) {
    val membership = state.membership
    when (membership.status) {
        MembershipStatus.APPROVED -> StickerCard(c, contentPadding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PopBadge("🎒", PopMint, c, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    PopText("Lớp ${membership.className}", c.text, 18, weight = FontWeight.Black)
                    PopText(membership.schoolName, c.textSoft, 14)
                    PopText("Giáo viên: ${membership.teacherName}", c.textSoft, 13)
                }
            }
            Spacer(Modifier.height(14.dp))
            PopPillButton("Thông báo & quản lý lớp", c, onOpenClassroom, fill = c.card)
        }

        MembershipStatus.PENDING -> StickerCard(c, contentPadding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PopBadge("⏳", PopSun, c, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    PopText("Đang chờ giáo viên duyệt", c.text, 17, weight = FontWeight.ExtraBold)
                    PopText("Lớp ${membership.className} · ${membership.schoolName}", c.textSoft, 14)
                }
            }
            Spacer(Modifier.height(14.dp))
            PopPillButton("Xem chi tiết", c, onOpenClassroom, fill = c.card)
        }

        else -> StickerCard(c, contentPadding = 18.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PopBadge("🏫", PopAzure, c, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    PopText("Bạn chưa vào lớp nào", c.text, 17, weight = FontWeight.ExtraBold)
                    PopText(
                        "Vào lớp để xem thời khóa biểu, bạn cùng lớp và nhận thông báo từ thầy cô.",
                        c.textSoft,
                        14
                    )
                }
            }
            if (membership.status == MembershipStatus.REJECTED) {
                Spacer(Modifier.height(8.dp))
                PopText("Yêu cầu trước chưa được duyệt, bạn có thể chọn lại lớp khác.", c.textSoft, 13)
            }
            Spacer(Modifier.height(14.dp))
            PopButton("Chọn trường và lớp", c, onOpenClassroom)
        }
    }
}

/* ───────────────────────── Thời khóa biểu ───────────────────────── */

@Composable
private fun TimetableCard(
    entries: List<TimetableEntry>,
    error: String?,
    c: PopPalette,
    onRetry: () -> Unit
) {
    val todayCalendarDay = remember { Calendar.getInstance().get(Calendar.DAY_OF_WEEK) }
    val today = remember(todayCalendarDay) { TimetableRules.schoolWeekday(todayCalendarDay) }
    var selectedDay by rememberSaveable { mutableIntStateOf(TimetableRules.defaultDay(todayCalendarDay)) }
    val counts = remember(entries) { TimetableRules.countsByDay(entries) }

    StickerCard(c, contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PopBadge("🗓️", PopPink, c, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                PopText("Thời khóa biểu", c.text, 16, weight = FontWeight.ExtraBold)
                PopText("Chạm vào từng ngày để xem các tiết học", c.textSoft, 13)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (error != null) {
            PopErrorBanner(error, c, actionLabel = "Thử lại", onAction = onRetry)
            return@StickerCard
        }
        if (entries.isEmpty()) {
            PopText("Giáo viên chưa nhập thời khóa biểu cho lớp của bạn.", c.textSoft, 14)
            return@StickerCard
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TimetableRules.SCHOOL_DAYS.forEach { day ->
                DayChip(
                    label = "T$day",
                    selected = day == selectedDay,
                    isToday = day == today,
                    hasClasses = (counts[day] ?: 0) > 0,
                    c = c,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedDay = day }
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        val heading = when {
            selectedDay == today -> "Hôm nay · ${TimetableRules.dayLabel(selectedDay)}"
            today == null && selectedDay == TimetableRules.SCHOOL_DAYS.first() ->
                "${TimetableRules.dayLabel(selectedDay)} tuần tới"
            else -> TimetableRules.dayLabel(selectedDay)
        }
        PopText(heading, c.accent, 14, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(6.dp))

        val groups = TimetableRules.groupBySession(entries, selectedDay)
        if (groups.isEmpty()) {
            PopText("Ngày này không có tiết nào. Bạn được nghỉ ngơi 🎉", c.textSoft, 14)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                groups.forEach { (session, items) ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        PopText(TimetableRules.sessionLabel(session), c.textSoft, 13, weight = FontWeight.Bold)
                        items.forEach { entry -> PeriodRow(entry, c) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChip(
    label: String,
    selected: Boolean,
    isToday: Boolean,
    hasClasses: Boolean,
    c: PopPalette,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = modifier
            .heightIn(min = 52.dp)
            .clip(shape)
            .background(if (selected) PopAzure else c.field)
            .border(2.dp, if (selected) c.outline else c.fieldLine, shape)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            )
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PopText(label, if (selected) Color.White else c.text, 15, weight = FontWeight.Black, align = TextAlign.Center)
        Spacer(Modifier.height(3.dp))
        // Chấm nhỏ: ngày có tiết học (vàng) hoặc ngày hôm nay (hồng).
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(
                    when {
                        isToday -> PopPink
                        hasClasses -> if (selected) Color.White.copy(alpha = 0.85f) else PopSun
                        else -> Color.Transparent
                    }
                )
        )
    }
}

@Composable
private fun PeriodRow(entry: TimetableEntry, c: PopPalette) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.5.dp, c.fieldLine, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PopText(
            "Tiết ${entry.period}",
            c.accent,
            13,
            Modifier.width(56.dp),
            FontWeight.ExtraBold
        )
        PopText(entry.subject, c.text, 15, Modifier.weight(1f), FontWeight.Bold)
    }
}

/* ───────────────────────── AI Giáo sư bài tập ───────────────────────── */

@Composable
private fun HomeworkAiCard(c: PopPalette, onOpen: () -> Unit) {
    StickerCard(
        c,
        fill = PopSun.copy(alpha = 0.25f).compositeOver(c.card),
        contentPadding = 18.dp,
        onClick = onOpen,
        onClickLabel = "Mở AI Giáo sư bài tập"
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PopBadge("🎓", PopSun, c, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                PopText("AI Giáo sư bài tập", c.text, 17, weight = FontWeight.Black)
                PopText(
                    "Hỏi bài, nhờ giải thích từng bước để hiểu chứ không chỉ chép đáp án.",
                    c.textSoft,
                    14
                )
            }
            PopText("›", c.accent, 26, weight = FontWeight.Black)
        }
        Spacer(Modifier.height(14.dp))
        PopButton("Hỏi Giáo sư ngay", c, onOpen)
    }
}

/* ───────────────────────── Thành viên lớp ───────────────────────── */

@Composable
private fun MembersCard(roster: ClassRoster?, error: String?, c: PopPalette, onRetry: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    StickerCard(c, contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PopBadge("👥", PopAzure, c, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                PopText("Thành viên lớp", c.text, 16, weight = FontWeight.ExtraBold)
                if (roster != null) PopText("${roster.members.size} học sinh", c.textSoft, 13)
            }
        }
        Spacer(Modifier.height(12.dp))

        if (roster == null) {
            if (error != null) PopErrorBanner(error, c, actionLabel = "Thử lại", onAction = onRetry)
            else PopLoadingDots(c.accent)
            return@StickerCard
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (roster.teacherName.isNotBlank()) {
                MemberRow(name = roster.teacherName, tag = "Giáo viên", isMe = false, teacher = true, c = c)
            }
            val visible: List<ClassMember> =
                if (expanded) roster.members else roster.members.take(COLLAPSED_MEMBER_COUNT)
            visible.forEach { member ->
                MemberRow(name = member.name, tag = if (member.isMe) "Bạn" else null, isMe = member.isMe, teacher = false, c = c)
            }
            if (roster.members.isEmpty()) {
                PopText("Chưa có học sinh nào trong lớp.", c.textSoft, 14)
            }
        }

        if (roster.members.size > COLLAPSED_MEMBER_COUNT) {
            Spacer(Modifier.height(10.dp))
            PopPillButton(
                text = if (expanded) "Thu gọn" else "Xem tất cả (${roster.members.size})",
                c = c,
                onClick = { expanded = !expanded },
                fill = c.card
            )
        }
    }
}

@Composable
private fun MemberRow(name: String, tag: String?, isMe: Boolean, teacher: Boolean, c: PopPalette) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (isMe) PopAzure.copy(alpha = 0.12f) else c.card)
            .border(1.5.dp, if (isMe) PopAzure else c.fieldLine, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PopBadge(if (teacher) "🧑‍🏫" else "🙂", if (teacher) PopSun else PopMint, c, size = 32.dp)
        Spacer(Modifier.width(10.dp))
        PopText(name, c.text, 15, Modifier.weight(1f), FontWeight.Bold)
        if (tag != null) {
            Spacer(Modifier.width(8.dp))
            PopText(
                tag,
                if (teacher || isMe) c.accent else c.textSoft,
                12,
                Modifier
                    .clip(CircleShape)
                    .background(c.accent.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                FontWeight.ExtraBold
            )
        }
    }
}

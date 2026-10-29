package com.health.pop.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.health.pop.ui.common.PopAzure
import com.health.pop.ui.common.PopBadge
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopText

/** Hai mục của tab "Kế hoạch": lên kế hoạch (trước) và nhìn lại ngày hôm nay (sau). */
enum class HabitsTab { PLAN, JOURNAL }

/** Tiêu đề một thẻ: biểu tượng + câu hỏi dễ hiểu, kèm dòng gợi ý nhỏ bên dưới (nếu có). */
@Composable
internal fun SectionHeader(
    emoji: String,
    color: Color,
    title: String,
    c: PopPalette,
    hint: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PopBadge(emoji, color, c, size = 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            PopText(title, c.text, 16, weight = FontWeight.ExtraBold)
            if (hint != null) PopText(hint, c.textSoft, 13)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/** Hàng ô chọn một giá trị (số + đơn vị): ô được chọn nền xanh, chữ trắng. */
@Composable
internal fun ChoiceRow(
    options: List<Int>,
    selected: Int,
    unit: String,
    c: PopPalette,
    onSelect: (Int) -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
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
                        onClick = { onSelect(option) }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PopText("$option", if (isSelected) Color.White else c.text, 20, weight = FontWeight.Black, align = TextAlign.Center)
                PopText(
                    unit,
                    if (isSelected) Color.White.copy(alpha = 0.85f) else c.textSoft,
                    11,
                    weight = FontWeight.SemiBold,
                    align = TextAlign.Center
                )
            }
        }
    }
}

/** Chip chọn nhiều: được chọn thì nền xanh, chữ trắng, viền đậm. */
@Composable
internal fun ToggleChip(
    text: String,
    selected: Boolean,
    c: PopPalette,
    onToggle: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .heightIn(min = 44.dp)
            .clip(shape)
            .background(if (selected) PopAzure else c.field)
            .border(2.dp, if (selected) c.outline else c.fieldLine, shape)
            .toggleable(
                value = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onValueChange = { onToggle() }
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            PopText("✓", Color.White, 14, weight = FontWeight.Black)
            Spacer(Modifier.width(6.dp))
        }
        PopText(text, if (selected) Color.White else c.text, 14, weight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/**
 * Hai nút lớn để chuyển giữa "Kế hoạch" và "Nhật ký". Mỗi nút có một dòng mô tả bằng lời thường
 * để người mới nhìn vào là hiểu mục đó dùng làm gì.
 */
@Composable
internal fun HabitsTabSwitch(
    selected: HabitsTab,
    onSelect: (HabitsTab) -> Unit,
    c: PopPalette
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        TabButton("📋", "Kế hoạch", "Mình sẽ làm gì?", selected == HabitsTab.PLAN, c, Modifier.weight(1f)) {
            onSelect(HabitsTab.PLAN)
        }
        TabButton("📝", "Nhật ký", "Hôm nay mình thế nào?", selected == HabitsTab.JOURNAL, c, Modifier.weight(1f)) {
            onSelect(HabitsTab.JOURNAL)
        }
    }
}

@Composable
private fun TabButton(
    emoji: String,
    title: String,
    subtitle: String,
    selected: Boolean,
    c: PopPalette,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
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
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PopText("$emoji  $title", if (selected) Color.White else c.text, 16, weight = FontWeight.Black, align = TextAlign.Center)
        PopText(
            subtitle,
            if (selected) Color.White.copy(alpha = 0.9f) else c.textSoft,
            12,
            weight = FontWeight.SemiBold,
            align = TextAlign.Center
        )
    }
}

/** Phần thu gọn cho các ô ít dùng: mặc định đóng để màn hình gọn, bấm vào mới mở. */
@Composable
internal fun MoreOptions(
    title: String,
    c: PopPalette,
    content: @Composable () -> Unit
) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .clickable(role = Role.Button, onClickLabel = if (open) "Thu gọn" else "Mở rộng") { open = !open }
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PopText(title, c.accent, 15, Modifier.weight(1f), FontWeight.ExtraBold)
            PopText(if (open) "▲" else "▼", c.accent, 13, weight = FontWeight.Black)
        }
        if (open) {
            Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                content()
            }
        }
    }
}

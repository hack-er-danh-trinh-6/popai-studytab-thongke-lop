package com.health.pop.ui.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.health.pop.ui.common.PopBadge
import com.health.pop.ui.common.PopMint
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopPillButton
import com.health.pop.ui.common.PopSun
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.StickerCard

@Composable
internal fun FocusSoundCard(
    c: PopPalette,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    StickerCard(
        c,
        fill = if (enabled) PopMint.copy(alpha = 0.18f).compositeOver(c.card) else c.field,
        contentPadding = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PopBadge("🎧", PopMint, c)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                PopText("Nhạc tập trung", c.text, 16, weight = FontWeight.ExtraBold)
                PopText(
                    if (enabled) "Tự phát nhạc nhẹ (30% âm lượng) khi đồng hồ chạy" else "Đang tắt",
                    c.textSoft, 13
                )
            }
            Spacer(Modifier.width(8.dp))
            PopPillButton(
                text = if (enabled) "Tắt" else "Bật",
                c = c,
                fill = if (enabled) c.card else PopSun,
                onClick = { onToggle(!enabled) }
            )
        }
        if (enabled) {
            Spacer(Modifier.height(8.dp))
            PopText("Cần kết nối mạng để tải nhạc. Rút tai nghe sẽ tự dừng nhạc.", c.textSoft, 11)
        }
    }
}

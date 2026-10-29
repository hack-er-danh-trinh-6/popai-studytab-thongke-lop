package com.health.pop.ui.focus

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.health.pop.core.designsystem.theme.PopcornAmber
import com.health.pop.core.designsystem.theme.PopcornAmberLight
import com.health.pop.core.designsystem.theme.SteadyBlue

/**
 * Lớp phủ toàn màn hình hiện khi người dùng đang trong phiên tập trung nhưng đã rời ứng dụng
 * hoặc đang ở màn hình khác. Lớp phủ nuốt mọi thao tác chạm để không dùng được màn hình bên dưới;
 * lối thoát duy nhất là nút quay lại đồng hồ tập trung.
 */
@Composable
fun FocusLockOverlay(
    visible: Boolean,
    leftApp: Boolean,
    taskName: String,
    targetMinutes: Int,
    onReturn: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(200)),
        exit = fadeOut(animationSpec = tween(200))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF2171722))
                // Nuốt mọi lần chạm để không "xuyên" xuống màn hình phía dưới.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .systemBarsPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(28.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(PopcornAmberLight, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = PopcornAmber,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Bạn đang trong thời gian tập trung",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (leftApp) {
                        "Bạn vừa rời khỏi ứng dụng khi đồng hồ vẫn đang chạy. Hãy quay lại để giữ nhịp tập trung."
                    } else {
                        "Bạn đang ở màn hình khác. Hãy quay lại đồng hồ tập trung để tiếp tục phiên."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "${taskName.ifBlank { "Tập trung học tập" }} · $targetMinutes phút",
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    color = SteadyBlue
                )

                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onReturn,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = SteadyBlue)
                ) {
                    Text("Quay lại đồng hồ tập trung")
                }
            }
        }
    }
}

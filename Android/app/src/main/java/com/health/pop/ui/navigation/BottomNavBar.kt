package com.health.pop.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.health.pop.ui.common.PopInk
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopPink
import com.health.pop.ui.common.PopSun
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.rememberPopPalette

data class NavItem(
    val screen: Screen,
    val icon: ImageVector,
    val label: String
)

private val bottomNavItems = listOf(
    NavItem(Screen.Home, Icons.Rounded.Home, "Hôm nay"),
    NavItem(Screen.Plan, Icons.Rounded.Checklist, "Kế hoạch"),
    NavItem(Screen.Focus, Icons.Rounded.Timer, "Tập trung"),
    NavItem(Screen.Study, Icons.Rounded.School, "Học tập"),
    NavItem(Screen.Progress, Icons.Rounded.BarChart, "Tiến bộ")
)

/** Độ "nổi" của bóng cứng bên dưới thanh và nút tròn (cùng kiểu StickerCard / PopButton). */
private val ShadowDepth = 4.dp

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    // true khi đang trong phiên tập trung: thanh mờ đi, bấm vào chỉ báo "đang khoá" (do [onNavigate] xử lý).
    locked: Boolean = false
) {
    // Nhật ký nằm chung tab Kế hoạch; màn AI Giáo sư bài tập thuộc tab Học tập.
    val tabRoute = when (currentRoute) {
        Screen.Journal.route -> Screen.Plan.route
        Screen.HomeworkAI.route -> Screen.Study.route
        else -> currentRoute
    }
    val exactIndex = bottomNavItems.indexOfFirst { it.screen.route == tabRoute }
    val selectedIndex = when {
        currentRoute == Screen.AI.route -> -1
        exactIndex >= 0 -> exactIndex
        else -> 0
    }

    FloatingBottomBar(
        items = bottomNavItems,
        selectedIndex = selectedIndex,
        onSelect = { index -> onNavigate(bottomNavItems[index].screen) },
        fabIcon = Icons.Rounded.AutoAwesome,
        fabSelected = currentRoute == Screen.AI.route,
        onFabClick = { onNavigate(Screen.AI) },
        modifier = Modifier.alpha(if (locked) 0.45f else 1f)
    )
}

@Composable
private fun FloatingBottomBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    fabIcon: ImageVector,
    fabSelected: Boolean,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = rememberPopPalette()
    val barHeight = 68.dp
    val fabSize = 56.dp

    // Nền khung bằng đúng màu thân trang (PopPage) để thanh nổi liền mạch với nội dung phía trên,
    // kể cả vùng dưới thanh điều hướng hệ thống.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(c.card)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(c.fieldLine)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            StickerBar(
                c = c,
                modifier = Modifier
                    .weight(1f)
                    .height(barHeight + ShadowDepth)
            ) {
                BoxWithConstraints(Modifier.padding(6.dp)) {
                    val itemWidth = maxWidth / items.size
                    val indicatorVisible = selectedIndex in items.indices
                    val indicatorAlpha by animateFloatAsState(
                        targetValue = if (indicatorVisible) 1f else 0f,
                        animationSpec = tween(220),
                        label = "navIndicatorAlpha"
                    )
                    val indicatorX by animateDpAsState(
                        targetValue = itemWidth * selectedIndex.coerceIn(items.indices),
                        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
                        label = "navIndicatorX"
                    )

                    Box(
                        Modifier
                            // Đọc indicatorX trong lambda (pha layout) để lò xo trượt không kéo cả thanh recompose mỗi khung hình.
                            .offset { IntOffset(indicatorX.roundToPx(), 0) }
                            .alpha(indicatorAlpha)
                            .width(itemWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(PopSun)
                            .border(2.dp, c.outline, RoundedCornerShape(50))
                    )

                    Row(Modifier.fillMaxSize()) {
                        items.forEachIndexed { index, item ->
                            NavTab(
                                item = item,
                                selected = index == selectedIndex,
                                c = c,
                                onClick = { onSelect(index) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            StickerFab(
                icon = fabIcon,
                size = fabSize,
                trackHeight = barHeight,
                selected = fabSelected,
                c = c,
                onClick = onFabClick
            )
        }
    }
}

/** Thanh viên thuốc kiểu sticker: nền thẻ, viền đậm, bóng cứng ngay phía dưới. */
@Composable
private fun StickerBar(
    c: PopPalette,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(50)
    Box(modifier.padding(bottom = ShadowDepth)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = ShadowDepth)
                .clip(shape)
                .background(c.outline)
        )
        Box(
            Modifier
                .fillMaxSize()
                .clip(shape)
                .background(c.card)
                .border(2.dp, c.outline, shape),
            content = content
        )
    }
}

@Composable
private fun NavTab(
    item: NavItem,
    selected: Boolean,
    c: PopPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Ô được chọn nằm trên nền vàng nên dùng chữ mực; ô còn lại dùng chữ nhạt của bảng màu.
    val tint by animateColorAsState(
        targetValue = if (selected) PopInk else c.textSoft,
        animationSpec = tween(220),
        label = "navTint"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1.18f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 400f),
        label = "navIconScale"
    )
    val lift by animateDpAsState(
        targetValue = if (selected) (-1).dp else 1.dp,
        animationSpec = spring(stiffness = 500f),
        label = "navLift"
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .semantics {
                this.selected = selected
                contentDescription = item.label
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .offset(y = lift)
                .size(24.dp)
                .scale(iconScale)
        )
        Spacer(Modifier.height(3.dp))
        PopText(
            text = item.label,
            color = tint,
            size = 10,
            weight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** Nút tròn POP-AI: nổi lên với bóng cứng, lún xuống khi bấm (giống PopButton). */
@Composable
private fun StickerFab(
    icon: ImageVector,
    size: Dp,
    trackHeight: Dp,
    selected: Boolean,
    c: PopPalette,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val sink by animateDpAsState(
        targetValue = if (pressed) 3.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "navFabSink"
    )
    val breathe by rememberInfiniteTransition(label = "navFabBreathe").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "navFabBreatheValue"
    )

    // Căn giữa theo chiều cao thanh điều hướng để hai khối cùng một đường tâm.
    val top = (trackHeight - size) / 2
    Box(
        Modifier
            .width(size)
            .height(trackHeight + ShadowDepth)
    ) {
        Box(
            Modifier
                .size(size)
                .offset(y = top + ShadowDepth)
                .clip(CircleShape)
                .background(c.outline)
        )
        Box(
            modifier = Modifier
                .size(size)
                .offset(y = top + sink)
                .clip(CircleShape)
                .background(if (selected) PopSun else PopPink)
                .border(2.dp, c.outline, CircleShape)
                .semantics { contentDescription = "POP-AI" }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            // Có nhãn chữ "POP-AI" để người dùng mới biết nút tròn này là gì (trước đây chỉ có biểu tượng).
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PopInk,
                    // Hiệu ứng "thở" lặp vô hạn: đọc trong graphicsLayer (pha vẽ) thay vì recompose liên tục trên mọi tab.
                    modifier = Modifier.size(22.dp).graphicsLayer {
                        scaleX = breathe
                        scaleY = breathe
                    }
                )
                Spacer(Modifier.height(2.dp))
                PopText(
                    text = "POP-AI",
                    color = PopInk,
                    size = 9,
                    weight = FontWeight.ExtraBold,
                    maxLines = 1
                )
            }
        }
    }
}

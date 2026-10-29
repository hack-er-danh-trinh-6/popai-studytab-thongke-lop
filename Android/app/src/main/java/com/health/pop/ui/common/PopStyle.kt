/*
 * PopStyle.kt — bộ thành phần giao diện "sticker" dùng chung, lấy từ màn Đăng nhập / Đăng ký (PopAuthScreen).
 *
 *  - Viền đậm + bóng cứng (không mờ), màu pastel, nhân vật Pop, nút nổi lún xuống khi bấm.
 *  - Chỉ dùng compose foundation / animation / ui: không cần Material, ảnh hay font ngoài.
 *  - Tự đổi sáng/tối theo hệ thống (giống PopAuthScreen).
 *
 * PopAuthScreen vẫn giữ bản riêng (private) của các thành phần này để không ảnh hưởng màn đăng nhập.
 * Khi muốn gộp, chỉ cần cho PopAuthScreen dùng lại file này.
 */
package com.health.pop.ui.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import kotlin.random.Random
import kotlinx.coroutines.delay

/* ───────────────────────── Bảng màu ───────────────────────── */

internal val PopInk = Color(0xFF14163A)
internal val PopAzure = Color(0xFF3D5AFE)
internal val PopSun = Color(0xFFFFD43B)
internal val PopPink = Color(0xFFFF6FA5)
internal val PopMint = Color(0xFF2FE0A9)
internal val PopCoral = Color(0xFFFF5A5F)

/** Một dòng này là chỗ đổi font toàn bộ kit (ví dụ Baloo 2 / Nunito qua Google Fonts). */
internal val PopFont: FontFamily = FontFamily.Default

@Immutable
internal class PopPalette(
    val heroTop: Color,
    val heroBottom: Color,
    val card: Color,
    val field: Color,
    val fieldLine: Color,
    val text: Color,
    val textSoft: Color,
    val outline: Color,
    val accent: Color,
)

internal val PopLightPalette = PopPalette(
    heroTop = PopAzure,
    heroBottom = Color(0xFF52B2FF),
    card = Color.White,
    field = Color(0xFFF1F3FF),
    fieldLine = PopInk.copy(alpha = 0.14f),
    text = PopInk,
    textSoft = PopInk.copy(alpha = 0.62f),
    outline = PopInk,
    accent = PopAzure,
)

internal val PopDarkPalette = PopPalette(
    heroTop = Color(0xFF1B2475),
    heroBottom = Color(0xFF2E63D1),
    card = Color(0xFF181B43),
    field = Color(0xFF23275C),
    fieldLine = Color.White.copy(alpha = 0.16f),
    text = Color(0xFFF4F5FF),
    textSoft = Color.White.copy(alpha = 0.68f),
    outline = Color(0xFF0A0B24),
    accent = Color(0xFF9DB0FF),
)

@Composable
internal fun rememberPopPalette(): PopPalette =
    if (isSystemInDarkTheme()) PopDarkPalette else PopLightPalette

/* ───────────────────────── Chữ & bóng ───────────────────────── */

@Composable
internal fun PopText(
    text: String,
    color: Color,
    size: Int,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Medium,
    align: TextAlign = TextAlign.Start,
    spacing: Float = 0f,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = weight,
            fontFamily = PopFont,
            textAlign = align,
            letterSpacing = spacing.sp,
        ),
        overflow = overflow,
        maxLines = maxLines,
    )
}

/** Bóng đổ cứng kiểu "sticker" (không mờ). */
internal fun Modifier.hardShadow(color: Color, radius: Dp, dy: Dp = 4.dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, dy.toPx()),
        size = size,
        cornerRadius = CornerRadius(radius.toPx()),
    )
}

/** Cho khối phía dưới chồng lên phần đầu [amount] mà vẫn tính đúng chiều cao. */
internal fun Modifier.overlapTop(amount: Dp): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = amount.roundToPx()
    layout(placeable.width, placeable.height - shift) { placeable.place(0, -shift) }
}

private fun DrawScope.sparkle(center: Offset, r: Float, color: Color) {
    val k = r * 0.24f
    val p = Path().apply {
        moveTo(center.x, center.y - r)
        lineTo(center.x + k, center.y - k)
        lineTo(center.x + r, center.y)
        lineTo(center.x + k, center.y + k)
        lineTo(center.x, center.y + r)
        lineTo(center.x - k, center.y + k)
        lineTo(center.x - r, center.y)
        lineTo(center.x - k, center.y - k)
        close()
    }
    drawPath(p, color)
}

/** Bong bóng trang trí (tĩnh) phía sau phần đầu — "pop" như bong bóng xà phòng. */
internal fun Modifier.popHeroBubbles(): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    drawCircle(Color.White.copy(alpha = 0.10f), w * 0.30f, Offset(-w * 0.04f, h * 0.10f))
    drawCircle(Color.White.copy(alpha = 0.08f), w * 0.22f, Offset(w, h * 0.62f))
    drawCircle(Color.White.copy(alpha = 0.6f), 9.dp.toPx(), Offset(w * 0.16f, h * 0.66f), style = Stroke(2.dp.toPx()))
    drawCircle(Color.White.copy(alpha = 0.6f), 5.dp.toPx(), Offset(w * 0.90f, h * 0.30f), style = Stroke(2.dp.toPx()))
    drawCircle(PopPink, 6.dp.toPx(), Offset(w * 0.80f, h * 0.82f))
    drawCircle(PopMint, 4.5f.dp.toPx(), Offset(w * 0.14f, h * 0.84f))
    sparkle(Offset(w * 0.86f, h * 0.58f), 11.dp.toPx(), PopSun)
    sparkle(Offset(w * 0.24f, h * 0.52f), 8.dp.toPx(), Color.White)
}

/* ───────────────────────── Icon status bar ───────────────────────── */

/** Phần đầu màn hình là nền xanh nên icon status bar cần màu sáng (cả chế độ sáng lẫn tối). */
@Composable
internal fun PopLightStatusBarIcons() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = view.context.findPopActivity()?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val wasLight = controller.isAppearanceLightStatusBars
            controller.isAppearanceLightStatusBars = false
            onDispose { controller.isAppearanceLightStatusBars = wasLight }
        }
    }
}

private tailrec fun Context.findPopActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findPopActivity()
    else -> null
}

/* ───────────────────────── Pop: nhân vật ───────────────────────── */

internal enum class PopMood { Happy, Oops }

@Composable
internal fun PopSpeechBubble(text: String) {
    val shape = RoundedCornerShape(18.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .widthIn(max = 320.dp)
                .hardShadow(PopInk, 18.dp, dy = 4.dp)
                .clip(shape)
                .background(Color.White)
                .border(2.dp, PopInk, shape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = text,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) },
                label = "bubble",
            ) { t -> PopText(t, PopInk, 15, weight = FontWeight.Bold, align = TextAlign.Center) }
        }
        Canvas(Modifier.size(width = 22.dp, height = 12.dp).offset(y = (-2).dp)) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(tail, Color.White)
            drawLine(PopInk, Offset(0f, 0f), Offset(size.width / 2f, size.height), 2.dp.toPx(), StrokeCap.Round)
            drawLine(PopInk, Offset(size.width, 0f), Offset(size.width / 2f, size.height), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
internal fun PopMascot(mood: PopMood, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "pop")
    val bob by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )

    var blinking by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2200, 4400))
            blinking = true
            delay(130)
            blinking = false
        }
    }
    val eyeOpen by animateFloatAsState(if (blinking) 0.08f else 1f, tween(70), label = "eye")
    val smile by animateFloatAsState(
        if (mood == PopMood.Happy) 1f else -0.9f,
        spring(stiffness = Spring.StiffnessMedium),
        label = "smile",
    )
    val sweat by animateFloatAsState(if (mood == PopMood.Oops) 1f else 0f, tween(250), label = "sweat")

    Canvas(
        modifier.graphicsLayer {
            translationY = -bob * 7.dp.toPx()
            rotationZ = (bob - 0.5f) * 4f
        },
    ) {
        val s = size.minDimension
        val line = s * 0.03f
        val c = Offset(s * 0.5f, s * 0.58f)
        val r = s * 0.37f

        // ăng-ten + bóng cứng + thân
        drawCircle(PopInk, r, c + Offset(s * 0.03f, s * 0.04f))
        drawLine(PopInk, Offset(c.x, c.y - r), Offset(c.x, s * 0.1f), line, StrokeCap.Round)
        drawCircle(PopInk, s * 0.065f, Offset(c.x, s * 0.085f))
        drawCircle(PopPink, s * 0.047f, Offset(c.x, s * 0.085f))
        drawCircle(PopSun, r, c)
        drawCircle(PopInk, r, c, style = Stroke(line))
        drawArc(
            Color.White.copy(alpha = 0.75f), 200f, 48f, false,
            Offset(c.x - r * 0.76f, c.y - r * 0.76f), Size(r * 1.52f, r * 1.52f),
            style = Stroke(s * 0.032f, cap = StrokeCap.Round),
        )

        // mắt + má
        val eyeY = c.y - r * 0.1f
        val eyeDx = r * 0.36f
        val eyeW = s * 0.075f
        val eyeH = s * 0.12f * eyeOpen
        listOf(-1f, 1f).forEach { side ->
            val ex = c.x + side * eyeDx
            drawOval(PopInk, Offset(ex - eyeW / 2f, eyeY - eyeH / 2f), Size(eyeW, eyeH))
            if (eyeOpen > 0.5f) {
                drawCircle(Color.White, s * 0.016f, Offset(ex - eyeW * 0.15f, eyeY - eyeH * 0.2f))
            }
            drawCircle(PopPink.copy(alpha = 0.8f), s * 0.05f, Offset(c.x + side * r * 0.62f, c.y + r * 0.22f))
        }

        // miệng (cười ↔ buồn)
        val mouthY = c.y + r * 0.22f
        val mouthW = r * 0.2f
        val bend = s * 0.07f * smile
        val mouth = Path().apply {
            moveTo(c.x - mouthW, mouthY)
            cubicTo(c.x - mouthW * 0.5f, mouthY + bend, c.x + mouthW * 0.5f, mouthY + bend, c.x + mouthW, mouthY)
        }
        drawPath(mouth, PopInk, style = Stroke(line, cap = StrokeCap.Round))

        // giọt mồ hôi khi có lỗi
        if (sweat > 0.01f) {
            val d = Offset(c.x + r * 0.92f, c.y - r * 0.45f)
            val dr = s * 0.04f
            val drop = Path().apply {
                moveTo(d.x, d.y - dr * 2.3f)
                lineTo(d.x - dr * 0.92f, d.y - dr * 0.35f)
                lineTo(d.x + dr * 0.92f, d.y - dr * 0.35f)
                close()
            }
            drawPath(drop, Color.White.copy(alpha = sweat))
            drawCircle(Color.White.copy(alpha = sweat), dr, d)
        }
    }
}

/* ───────────────────────── Thẻ, nút, huy hiệu ───────────────────────── */

/**
 * Thẻ "sticker": viền đậm + bóng cứng bên dưới. Có [onClick] thì lún xuống khi bấm.
 * Màu nền [fill] phải đặc (không trong suốt) vì bóng cứng nằm ngay phía sau.
 */
@Composable
internal fun StickerCard(
    c: PopPalette,
    modifier: Modifier = Modifier,
    fill: Color = c.field,
    radius: Dp = 22.dp,
    contentPadding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val depth = 4.dp
    val sink by animateDpAsState(
        if (pressed && onClick != null) 3.dp else 0.dp,
        spring(stiffness = Spring.StiffnessHigh),
        label = "cardSink",
    )
    val shape = RoundedCornerShape(radius)
    Box(modifier.padding(bottom = depth)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = depth)
                .clip(shape)
                .background(c.outline),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = sink)
                .clip(shape)
                .background(fill)
                .border(2.dp, c.outline, shape)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = source,
                            indication = null,
                            role = Role.Button,
                            onClickLabel = onClickLabel,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

/** Nút "sticker": nổi lên với bóng cứng, lún xuống khi bấm. */
@Composable
internal fun PopButton(
    text: String,
    c: PopPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    fill: Color = PopSun,
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val depth = 6.dp
    val sink by animateDpAsState(
        if (pressed && !loading) 4.dp else 0.dp,
        spring(stiffness = Spring.StiffnessHigh),
        label = "sink",
    )
    val shape = RoundedCornerShape(18.dp)
    Box(modifier.fillMaxWidth().height(54.dp + depth)) {
        Box(
            Modifier.fillMaxWidth().height(54.dp).offset(y = depth).clip(shape).background(c.outline),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .offset(y = sink)
                .clip(shape)
                .background(fill)
                .border(2.dp, c.outline, shape)
                .clickable(
                    interactionSource = source,
                    indication = null,
                    enabled = !loading,
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) PopLoadingDots(PopInk) else PopText(text, PopInk, 16, weight = FontWeight.ExtraBold)
        }
    }
}

@Composable
internal fun PopLoadingDots(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(3) { i ->
            val lift by t.animateFloat(
                initialValue = 0f,
                targetValue = -7f,
                animationSpec = infiniteRepeatable(
                    tween(380, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(i * 130),
                ),
                label = "dot$i",
            )
            Box(Modifier.offset(y = lift.dp).size(9.dp).clip(CircleShape).background(color))
        }
    }
}

/** Nút viên thuốc nhỏ (viền đậm, nền đặc) cho hành động phụ như "Thêm", "Vào lớp", "Ghi lại". */
@Composable
internal fun PopPillButton(
    text: String,
    c: PopPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Color = PopSun,
) {
    Box(
        modifier
            .heightIn(min = 44.dp)
            .alpha(if (enabled) 1f else 0.55f)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, c.outline, CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        PopText(text, PopInk, 14, weight = FontWeight.ExtraBold, maxLines = 1)
    }
}

/** Huy hiệu tròn pastel chứa một emoji (giống biểu tượng trong ô nhập của màn đăng nhập). */
@Composable
internal fun PopBadge(
    emoji: String,
    color: Color,
    c: PopPalette,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier.size(size).clip(CircleShape).background(color.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center,
    ) { PopText(emoji, c.text, (size.value * 0.46f).toInt()) }
}

/* ───────────────────────── Khung trang, ô nhập, banner lỗi ───────────────────────── */

/**
 * Khung một trang theo phong cách màn đăng nhập: nền xanh + [label]/[title] + bong bóng thoại + Pop,
 * phía dưới là thẻ [body] bo góc chồng lên phần đầu. Tự cuộn, tự xử lý status bar, và chỉ chạy
 * hiệu ứng vào màn hình ở lần đầu (rememberSaveable nên quay lại tab không chạy lại).
 *
 * Trang dùng khung này phải được AppNavHost vẽ tràn (không áp padding trên của Scaffold).
 */
@Composable
internal fun PopPage(
    c: PopPalette,
    label: String,
    title: String,
    bubble: String,
    mood: PopMood,
    modifier: Modifier = Modifier,
    heroActions: (@Composable RowScope.() -> Unit)? = null,
    mascotSize: Dp = 100.dp,
    body: @Composable ColumnScope.() -> Unit,
) {
    PopLightStatusBarIcons()

    val isPreview = LocalInspectionMode.current
    var entered by rememberSaveable { mutableStateOf(isPreview) }
    LaunchedEffect(Unit) { entered = true }
    val popIn by animateFloatAsState(
        if (entered) 1f else 0f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow),
        label = "pagePopIn",
    )
    val cardIn by animateFloatAsState(
        if (entered) 1f else 0f,
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "pageCardIn",
    )

    Column(
        modifier
            .fillMaxSize()
            .background(c.card)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(c.heroTop, c.heroBottom)))
                .popHeroBubbles()
                .statusBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 12.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    PopText(
                        label, Color.White.copy(alpha = 0.85f), 12,
                        weight = FontWeight.Bold, spacing = 1.1f, maxLines = 1,
                    )
                    Spacer(Modifier.height(4.dp))
                    PopText(
                        title, Color.White, 24,
                        weight = FontWeight.Black, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
                if (heroActions != null) {
                    Spacer(Modifier.width(8.dp))
                    heroActions()
                }
            }
            Spacer(Modifier.height(16.dp))
            PopSpeechBubble(bubble)
            PopMascot(
                mood,
                Modifier
                    .size(mascotSize)
                    .graphicsLayer {
                        scaleX = popIn
                        scaleY = popIn
                    },
            )
        }

        Column(
            Modifier
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = body,
        )
    }
}

/**
 * Ô nhập một dòng theo kiểu màn đăng nhập. [label], [badge] và [trailing] đều tùy chọn.
 * Viền đổi màu khi focus (accent) hoặc có [error] (đỏ).
 */
@Composable
internal fun PopField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    c: PopPalette,
    modifier: Modifier = Modifier,
    label: String? = null,
    badge: String? = null,
    badgeColor: Color = PopAzure,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
    onImeAction: () -> Unit = {},
    clearFocusOnDone: Boolean = true,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    trailing: (@Composable () -> Unit)? = null,
) {
    val focus = LocalFocusManager.current
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val line by animateColorAsState(
        when {
            error != null -> PopCoral
            focused -> c.accent
            else -> c.fieldLine
        },
        tween(160),
        label = "fieldLine",
    )
    var lastError by remember { mutableStateOf("") }
    if (error != null) lastError = error
    val shape = RoundedCornerShape(16.dp)

    Column(modifier) {
        if (label != null) {
            PopText(label, c.text, 13, Modifier.padding(start = 4.dp, bottom = 6.dp), FontWeight.Bold)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            textStyle = TextStyle(
                color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = PopFont,
            ),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { focus.moveFocus(FocusDirection.Down) },
                onDone = {
                    onImeAction()
                    if (clearFocusOnDone) focus.clearFocus()
                },
            ),
            interactionSource = source,
            decorationBox = { inner ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(shape)
                        .background(c.field)
                        .border(2.dp, line, shape)
                        .padding(start = if (badge != null) 10.dp else 16.dp, end = if (trailing != null) 6.dp else 16.dp)
                        .padding(vertical = if (singleLine) 0.dp else 12.dp),
                    verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
                ) {
                    if (badge != null) {
                        PopBadge(badge, badgeColor, c, size = 34.dp)
                        Spacer(Modifier.width(10.dp))
                    }
                    Box(Modifier.weight(1f).alpha(if (enabled) 1f else 0.6f)) {
                        if (value.isEmpty()) PopText(hint, c.text.copy(alpha = 0.38f), 16)
                        inner()
                    }
                    if (trailing != null) {
                        Spacer(Modifier.width(6.dp))
                        trailing()
                    }
                }
            },
        )
        AnimatedVisibility(visible = error != null) {
            PopText(lastError, PopCoral, 12, Modifier.padding(start = 4.dp, top = 6.dp), FontWeight.SemiBold)
        }
    }
}

/** Banner lỗi (đỏ nhạt, viền đỏ). Có [actionLabel] + [onAction] thì hiện thêm nút chữ bên phải. */
@Composable
internal fun PopErrorBanner(
    message: String,
    c: PopPalette,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(PopCoral.copy(alpha = 0.12f))
            .border(1.5.dp, PopCoral, shape)
            .padding(start = 12.dp, end = if (actionLabel != null) 4.dp else 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PopText("😕", c.text, 18)
        Spacer(Modifier.width(10.dp))
        PopText(message, c.text, 14, Modifier.weight(1f), FontWeight.SemiBold)
        if (actionLabel != null && onAction != null) {
            PopText(
                actionLabel, c.accent, 14,
                Modifier
                    .clip(CircleShape)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                FontWeight.ExtraBold,
            )
        }
    }
}

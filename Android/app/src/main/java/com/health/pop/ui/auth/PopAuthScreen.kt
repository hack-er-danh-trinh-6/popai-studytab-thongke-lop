/*
 * PopAuthScreen.kt — màn hình Đăng nhập / Đăng ký mới cho POP AI (Jetpack Compose).
 *
 *  - Chỉ dùng compose foundation / animation / ui: không cần Material, ảnh hay font ngoài.
 *  - Không đụng tới ViewModel: bạn truyền isLoading / errorMessage vào, nhận onLogin / onRegister ra.
 *  - Tự đổi sáng/tối theo hệ thống.
 *  - Nhân vật "Pop" phản ứng theo trạng thái: nhắm mắt khi nhập mật khẩu, toát mồ hôi khi có lỗi.
 */
package com.health.pop.ui.auth

import android.util.Patterns
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random
import kotlinx.coroutines.delay

/* ───────────────────────── API công khai ───────────────────────── */

enum class AuthTab { Login, Register }

/** Tài khoản đăng nhập bằng gì? Đổi sang Username nếu backend dùng tên đăng nhập. */
enum class AuthIdentifier(
    val label: String,
    val hint: String,
    val keyboard: KeyboardType,
    val badge: String,
) {
    Email("Email", "ban@email.com", KeyboardType.Email, "✉️"),
    Username("Tên đăng nhập", "ten.dang.nhap", KeyboardType.Text, "🙂"),
}

/** Khớp với mật khẩu tối thiểu của backend. */
private const val MIN_PASSWORD = 8

@Composable
fun PopAuthScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (identifier: String, password: String) -> Unit,
    onRegister: (name: String, identifier: String, password: String) -> Unit,
    modifier: Modifier = Modifier,
    initialTab: AuthTab = AuthTab.Login,
    identifier: AuthIdentifier = AuthIdentifier.Email,
    onForgotPassword: (() -> Unit)? = null,
    /** Gọi khi người dùng đổi tab hoặc gõ lại sau lỗi, để ViewModel xóa lỗi cũ. */
    onClearError: () -> Unit = {},
) {
    val c = if (isSystemInDarkTheme()) DarkColors else LightColors
    val focus = LocalFocusManager.current

    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var name by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var tried by remember { mutableStateOf(false) }
    var pwFocus by remember { mutableStateOf(setOf<String>()) }

    val isLogin = tab == AuthTab.Login
    val hasError = !errorMessage.isNullOrBlank()

    // ── kiểm tra dữ liệu ──
    val accountErr = when (identifier) {
        AuthIdentifier.Email ->
            if (Patterns.EMAIL_ADDRESS.matcher(account.trim()).matches()) null
            else "Nhập email theo dạng ten@email.com"
        AuthIdentifier.Username ->
            if (account.trim().length >= 3) null else "Tên đăng nhập cần ít nhất 3 ký tự"
    }
    val nameErr = if (name.isBlank()) "Nhập tên của bạn" else null
    val passErrLogin = if (pass.isEmpty()) "Nhập mật khẩu của bạn" else null
    val passErrRegister =
        if (pass.length < MIN_PASSWORD) "Mật khẩu cần ít nhất $MIN_PASSWORD ký tự" else null
    val pass2Err = if (pass2 == pass) null else "Mật khẩu nhập lại chưa khớp"

    fun submit() {
        tried = true
        val ok = if (isLogin) {
            accountErr == null && passErrLogin == null
        } else {
            nameErr == null && accountErr == null && passErrRegister == null && pass2Err == null
        }
        if (!ok) return
        focus.clearFocus()
        if (isLogin) onLogin(account.trim(), pass) else onRegister(name.trim(), account.trim(), pass)
    }

    fun switchTab(target: AuthTab) {
        if (target == tab) return
        tab = target
        tried = false
        pwFocus = emptySet()
        focus.clearFocus()
        onClearError()
    }

    fun edited() {
        if (hasError) onClearError()
    }

    // ── tâm trạng của Pop ──
    val mood = when {
        pwFocus.isNotEmpty() -> PopMood.Covering
        hasError -> PopMood.Oops
        else -> PopMood.Happy
    }
    val bubble = when (mood) {
        PopMood.Covering -> "Mình nhắm mắt rồi, bạn cứ nhập nhé 🙈"
        PopMood.Oops -> "Có trục trặc rồi. Xem thông báo bên dưới nhé."
        PopMood.Happy ->
            if (isLogin) "Chào bạn! Vào lại phiên tập trung nào 👋" else "Cùng lấy lại sự tập trung nhé ✨"
    }

    // ── hiệu ứng vào màn hình (một lần duy nhất) ──
    val isPreview = LocalInspectionMode.current
    var entered by remember { mutableStateOf(isPreview) }
    LaunchedEffect(Unit) { entered = true }
    val popIn by animateFloatAsState(
        if (entered) 1f else 0f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow),
        label = "popIn",
    )
    val cardIn by animateFloatAsState(
        if (entered) 1f else 0f,
        spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "cardIn",
    )

    Column(
        modifier
            .fillMaxSize()
            .background(c.card)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        // ═════════ PHẦN ĐẦU: nền xanh + Pop ═════════
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(c.heroTop, c.heroBottom)))
                .heroBubbles()
                .statusBarsPadding()
                .padding(top = 14.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PopText("POP AI", Color.White, 26, weight = FontWeight.Black, spacing = -0.5f)
            Spacer(Modifier.height(14.dp))
            SpeechBubble(bubble)
            PopMascot(
                mood,
                Modifier
                    .size(128.dp)
                    .graphicsLayer {
                        scaleX = popIn
                        scaleY = popIn
                    },
            )
        }

        // ═════════ THẺ FORM ═════════
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
                .padding(horizontal = 22.dp)
                .padding(top = 22.dp, bottom = 28.dp)
                .navigationBarsPadding(),
        ) {
            AuthTabs(tab, c, ::switchTab)
            Spacer(Modifier.height(20.dp))

            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxWidth(),
                transitionSpec = {
                    val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                    (slideInHorizontally(tween(280)) { it * dir / 6 } + fadeIn(tween(240))) togetherWith
                        (slideOutHorizontally(tween(200)) { -it * dir / 6 } + fadeOut(tween(120)))
                },
                label = "authForm",
            ) { current ->
                val login = current == AuthTab.Login
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (!login) {
                        PopField(
                            label = "Tên của bạn",
                            value = name,
                            onValueChange = { name = it; edited() },
                            hint = "Nguyễn Văn An",
                            badge = "🙂",
                            badgeColor = Sun,
                            c = c,
                            error = if (tried) nameErr else null,
                        )
                    }
                    PopField(
                        label = identifier.label,
                        value = account,
                        onValueChange = { account = it; edited() },
                        hint = identifier.hint,
                        badge = identifier.badge,
                        badgeColor = Azure,
                        c = c,
                        keyboardType = identifier.keyboard,
                        error = if (tried) accountErr else null,
                    )
                    Column {
                        PopField(
                            label = "Mật khẩu",
                            value = pass,
                            onValueChange = { pass = it; edited() },
                            hint = if (login) "Mật khẩu của bạn" else "Ít nhất $MIN_PASSWORD ký tự",
                            badge = "🔒",
                            badgeColor = Pink,
                            c = c,
                            isPassword = true,
                            error = if (tried) (if (login) passErrLogin else passErrRegister) else null,
                            imeAction = if (login) ImeAction.Done else ImeAction.Next,
                            onDone = ::submit,
                            onFocusChange = { f -> pwFocus = if (f) pwFocus + "pass" else pwFocus - "pass" },
                        )
                        if (!login) StrengthMeter(pass, c)
                    }
                    if (!login) {
                        PopField(
                            label = "Nhập lại mật khẩu",
                            value = pass2,
                            onValueChange = { pass2 = it; edited() },
                            hint = "Gõ lại mật khẩu ở trên",
                            badge = "🔁",
                            badgeColor = Mint,
                            c = c,
                            isPassword = true,
                            error = if (tried) pass2Err else null,
                            imeAction = ImeAction.Done,
                            onDone = ::submit,
                            onFocusChange = { f -> pwFocus = if (f) pwFocus + "pass2" else pwFocus - "pass2" },
                        )
                    }
                    if (login && onForgotPassword != null) {
                        PopText(
                            "Quên mật khẩu?", c.accent, 14,
                            Modifier
                                .align(Alignment.End)
                                .clip(CircleShape)
                                .clickable(onClick = onForgotPassword)
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            FontWeight.Bold,
                        )
                    }
                }
            }

            ErrorBanner(errorMessage, c)
            Spacer(Modifier.height(20.dp))
            PopButton(if (isLogin) "Đăng nhập" else "Tạo tài khoản", isLoading, c, ::submit)
            Spacer(Modifier.height(18.dp))
            SwitchLink(isLogin, c) { switchTab(if (isLogin) AuthTab.Register else AuthTab.Login) }
        }
    }
}

/* ───────────────────────── Bảng màu ───────────────────────── */

private val Ink = Color(0xFF14163A)
private val Azure = Color(0xFF3D5AFE)
private val Sun = Color(0xFFFFD43B)
private val Pink = Color(0xFFFF6FA5)
private val Mint = Color(0xFF2FE0A9)
private val Coral = Color(0xFFFF5A5F)

/** Một dòng này là chỗ đổi font toàn màn hình (ví dụ Baloo 2 / Nunito qua Google Fonts). */
private val PopFont: FontFamily = FontFamily.Default

@Immutable
private class PopColors(
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

private val LightColors = PopColors(
    heroTop = Azure,
    heroBottom = Color(0xFF52B2FF),
    card = Color.White,
    field = Color(0xFFF1F3FF),
    fieldLine = Ink.copy(alpha = 0.14f),
    text = Ink,
    textSoft = Ink.copy(alpha = 0.62f),
    outline = Ink,
    accent = Azure,
)

private val DarkColors = PopColors(
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

/* ───────────────────────── Thành phần nhỏ ───────────────────────── */

@Composable
private fun PopText(
    text: String,
    color: Color,
    size: Int,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Medium,
    align: TextAlign = TextAlign.Start,
    spacing: Float = 0f,
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
    )
}

/** Bóng đổ cứng kiểu "sticker" (không mờ). */
private fun Modifier.hardShadow(color: Color, radius: Dp, dy: Dp = 4.dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        topLeft = Offset(0f, dy.toPx()),
        size = size,
        cornerRadius = CornerRadius(radius.toPx()),
    )
}

/** Cho thẻ form chồng lên phần đầu [amount] mà vẫn tính đúng chiều cao. */
private fun Modifier.overlapTop(amount: Dp): Modifier = layout { measurable, constraints ->
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

/** Bong bóng trang trí (tĩnh) phía sau Pop — "pop" như bong bóng xà phòng. */
private fun Modifier.heroBubbles(): Modifier = drawBehind {
    val w = size.width
    val h = size.height
    drawCircle(Color.White.copy(alpha = 0.10f), w * 0.30f, Offset(-w * 0.04f, h * 0.10f))
    drawCircle(Color.White.copy(alpha = 0.08f), w * 0.22f, Offset(w, h * 0.62f))
    drawCircle(Color.White.copy(alpha = 0.6f), 9.dp.toPx(), Offset(w * 0.16f, h * 0.66f), style = Stroke(2.dp.toPx()))
    drawCircle(Color.White.copy(alpha = 0.6f), 5.dp.toPx(), Offset(w * 0.90f, h * 0.18f), style = Stroke(2.dp.toPx()))
    drawCircle(Pink, 6.dp.toPx(), Offset(w * 0.80f, h * 0.82f))
    drawCircle(Mint, 4.5f.dp.toPx(), Offset(w * 0.14f, h * 0.84f))
    sparkle(Offset(w * 0.84f, h * 0.60f), 11.dp.toPx(), Sun)
    sparkle(Offset(w * 0.24f, h * 0.52f), 8.dp.toPx(), Color.White)
}

/* ───────────────────────── Pop: nhân vật ───────────────────────── */

private enum class PopMood { Happy, Covering, Oops }

@Composable
private fun SpeechBubble(text: String) {
    val shape = RoundedCornerShape(18.dp)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .widthIn(max = 300.dp)
                .hardShadow(Ink, 18.dp, dy = 4.dp)
                .clip(shape)
                .background(Color.White)
                .border(2.dp, Ink, shape)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = text,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(100)) },
                label = "bubble",
            ) { t -> PopText(t, Ink, 15, weight = FontWeight.Bold, align = TextAlign.Center) }
        }
        Canvas(Modifier.size(width = 22.dp, height = 12.dp).offset(y = (-2).dp)) {
            val tail = Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(tail, Color.White)
            drawLine(Ink, Offset(0f, 0f), Offset(size.width / 2f, size.height), 2.dp.toPx(), StrokeCap.Round)
            drawLine(Ink, Offset(size.width, 0f), Offset(size.width / 2f, size.height), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
private fun PopMascot(mood: PopMood, modifier: Modifier = Modifier) {
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
    val cover by animateFloatAsState(
        if (mood == PopMood.Covering) 1f else 0f,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
        label = "cover",
    )
    val smile by animateFloatAsState(
        when (mood) {
            PopMood.Happy -> 1f
            PopMood.Covering -> 0.5f
            PopMood.Oops -> -0.9f
        },
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
        drawCircle(Ink, r, c + Offset(s * 0.03f, s * 0.04f))
        drawLine(Ink, Offset(c.x, c.y - r), Offset(c.x, s * 0.1f), line, StrokeCap.Round)
        drawCircle(Ink, s * 0.065f, Offset(c.x, s * 0.085f))
        drawCircle(Pink, s * 0.047f, Offset(c.x, s * 0.085f))
        drawCircle(Sun, r, c)
        drawCircle(Ink, r, c, style = Stroke(line))
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
            drawOval(Ink, Offset(ex - eyeW / 2f, eyeY - eyeH / 2f), Size(eyeW, eyeH))
            if (eyeOpen > 0.5f) {
                drawCircle(Color.White, s * 0.016f, Offset(ex - eyeW * 0.15f, eyeY - eyeH * 0.2f))
            }
            drawCircle(Pink.copy(alpha = 0.8f), s * 0.05f, Offset(c.x + side * r * 0.62f, c.y + r * 0.22f))
        }

        // miệng (cười ↔ buồn)
        val mouthY = c.y + r * 0.22f
        val mouthW = r * 0.2f
        val bend = s * 0.07f * smile
        val mouth = Path().apply {
            moveTo(c.x - mouthW, mouthY)
            cubicTo(c.x - mouthW * 0.5f, mouthY + bend, c.x + mouthW * 0.5f, mouthY + bend, c.x + mouthW, mouthY)
        }
        drawPath(mouth, Ink, style = Stroke(line, cap = StrokeCap.Round))

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

        // hai tay che mắt khi nhập mật khẩu
        if (cover > 0.01f) {
            val handY = s * 1.05f + (eyeY - s * 1.05f) * cover
            val hr = s * 0.1f
            listOf(-1f, 1f).forEach { side ->
                val hx = c.x + side * eyeDx
                drawCircle(Ink, hr, Offset(hx + s * 0.014f, handY + s * 0.018f))
                drawCircle(Sun, hr, Offset(hx, handY))
                drawCircle(Ink, hr, Offset(hx, handY), style = Stroke(line))
            }
        }
    }
}

/* ───────────────────────── Tab, ô nhập, nút ───────────────────────── */

@Composable
private fun AuthTabs(selected: AuthTab, c: PopColors, onSelect: (AuthTab) -> Unit) {
    val pos by animateFloatAsState(
        if (selected == AuthTab.Login) 0f else 1f,
        spring(dampingRatio = 0.7f, stiffness = 450f),
        label = "tabPos",
    )
    val pill = RoundedCornerShape(18.dp)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(pill)
            .background(c.field)
            .border(2.dp, c.outline, pill)
            .padding(4.dp),
    ) {
        val tabWidth = maxWidth / 2
        Box(
            Modifier
                .offset(x = tabWidth * pos)
                .width(tabWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(14.dp))
                .background(Azure),
        )
        Row(Modifier.fillMaxSize()) {
            listOf(AuthTab.Login to "Đăng nhập", AuthTab.Register to "Đăng ký").forEach { (t, label) ->
                val isSel = selected == t
                val textColor by animateColorAsState(
                    if (isSel) Color.White else c.textSoft, tween(180), label = "tabText",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSel,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(t) },
                        ),
                    contentAlignment = Alignment.Center,
                ) { PopText(label, textColor, 16, weight = FontWeight.ExtraBold) }
            }
        }
    }
}

@Composable
private fun PopField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    badge: String,
    badgeColor: Color,
    c: PopColors,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
    onFocusChange: (Boolean) -> Unit = {},
) {
    val focus = LocalFocusManager.current
    var revealed by remember { mutableStateOf(false) }
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val line by animateColorAsState(
        when {
            error != null -> Coral
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
        PopText(label, c.text, 13, Modifier.padding(start = 4.dp, bottom = 6.dp), FontWeight.Bold)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().onFocusChanged { onFocusChange(it.isFocused) },
            singleLine = true,
            textStyle = TextStyle(
                color = c.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, fontFamily = PopFont,
            ),
            cursorBrush = SolidColor(c.accent),
            visualTransformation =
                if (isPassword && !revealed) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                keyboardType = if (isPassword) KeyboardType.Password else keyboardType,
                imeAction = imeAction,
            ),
            keyboardActions = KeyboardActions(
                onNext = { focus.moveFocus(FocusDirection.Down) },
                onDone = { onDone() },
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
                        .padding(start = 10.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(badgeColor.copy(alpha = 0.25f)),
                        contentAlignment = Alignment.Center,
                    ) { PopText(badge, c.text, 16) }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty()) PopText(hint, c.text.copy(alpha = 0.38f), 16)
                        inner()
                    }
                    if (isPassword) {
                        PopText(
                            if (revealed) "Ẩn" else "Hiện", c.accent, 14,
                            Modifier
                                .clip(CircleShape)
                                .clickable { revealed = !revealed }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            FontWeight.ExtraBold,
                        )
                    }
                }
            },
        )
        AnimatedVisibility(visible = error != null) {
            PopText(lastError, Coral, 12, Modifier.padding(start = 4.dp, top = 6.dp), FontWeight.SemiBold)
        }
    }
}

private fun passwordScore(p: String): Int {
    if (p.isEmpty()) return 0
    var s = 0
    if (p.length >= 6) s++
    if (p.length >= 10) s++
    if (p.any { it.isDigit() } && p.any { it.isLetter() }) s++
    if ((p.any { it.isUpperCase() } && p.any { it.isLowerCase() }) || p.any { !it.isLetterOrDigit() }) s++
    return s.coerceIn(1, 4)
}

@Composable
private fun StrengthMeter(password: String, c: PopColors) {
    val score = passwordScore(password)
    val palette = listOf(Coral, Color(0xFFFF9F1C), Color(0xFF8BD450), Color(0xFF1FC98E))
    val labels = listOf("", "Yếu", "Tạm được", "Khá", "Mạnh")
    val active = palette[(score - 1).coerceAtLeast(0)]
    AnimatedVisibility(
        visible = password.isNotEmpty(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { i ->
                    val fill by animateColorAsState(
                        if (i < score) active else c.fieldLine, tween(220), label = "seg$i",
                    )
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(fill))
                }
            }
            Spacer(Modifier.width(12.dp))
            PopText(labels[score], active, 12, weight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ErrorBanner(message: String?, c: PopColors) {
    var last by remember { mutableStateOf("") }
    if (!message.isNullOrBlank()) last = message
    AnimatedVisibility(
        visible = !message.isNullOrBlank(),
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        val shape = RoundedCornerShape(14.dp)
        Row(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .clip(shape)
                .background(Coral.copy(alpha = 0.12f))
                .border(1.5.dp, Coral, shape)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PopText("😕", c.text, 18)
            Spacer(Modifier.width(10.dp))
            PopText(last, c.text, 14, weight = FontWeight.SemiBold)
        }
    }
}

/** Nút "sticker": nổi lên với bóng cứng, lún xuống khi bấm. */
@Composable
private fun PopButton(text: String, loading: Boolean, c: PopColors, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val depth = 6.dp
    val sink by animateDpAsState(
        if (pressed && !loading) 4.dp else 0.dp,
        spring(stiffness = Spring.StiffnessHigh),
        label = "sink",
    )
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth().height(58.dp + depth)) {
        Box(
            Modifier.fillMaxWidth().height(58.dp).offset(y = depth).clip(shape).background(c.outline),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .offset(y = sink)
                .clip(shape)
                .background(Sun)
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
            if (loading) LoadingDots(Ink) else PopText(text, Ink, 17, weight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun LoadingDots(color: Color) {
    val t = rememberInfiniteTransition(label = "dots")
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
private fun SwitchLink(isLogin: Boolean, c: PopColors, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PopText(if (isLogin) "Chưa có tài khoản?" else "Đã có tài khoản?", c.textSoft, 14)
        PopText(
            if (isLogin) "Đăng ký" else "Đăng nhập", c.accent, 14,
            Modifier
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            FontWeight.ExtraBold,
        )
    }
}

/* ───────────────────────── Preview (xóa nếu project chưa có ui-tooling-preview) ───────────────────────── */

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun PopAuthLoginPreview() {
    PopAuthScreen(
        isLoading = false,
        errorMessage = null,
        onLogin = { _, _ -> },
        onRegister = { _, _, _ -> },
    )
}

@Preview(
    showBackground = true, widthDp = 390, heightDp = 1000,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PopAuthRegisterDarkPreview() {
    PopAuthScreen(
        isLoading = false,
        errorMessage = "Email này đã có tài khoản. Hãy đăng nhập hoặc dùng email khác.",
        initialTab = AuthTab.Register,
        onLogin = { _, _ -> },
        onRegister = { _, _, _ -> },
    )
}

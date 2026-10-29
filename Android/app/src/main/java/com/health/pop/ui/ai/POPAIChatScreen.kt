package com.health.pop.ui.ai

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.health.pop.domain.model.AiMode
import com.health.pop.domain.model.ChatMessage
import com.health.pop.domain.model.ChatRole
import com.health.pop.domain.usecase.ValidateAiPromptUseCase
import com.health.pop.ui.common.PopAzure
import com.health.pop.ui.common.PopBadge
import com.health.pop.ui.common.PopCoral
import com.health.pop.ui.common.PopFont
import com.health.pop.ui.common.PopLightStatusBarIcons
import com.health.pop.ui.common.PopLoadingDots
import com.health.pop.ui.common.PopMascot
import com.health.pop.ui.common.PopMint
import com.health.pop.ui.common.PopMood
import com.health.pop.ui.common.PopPalette
import com.health.pop.ui.common.PopPillButton
import com.health.pop.ui.common.PopSun
import com.health.pop.ui.common.PopText
import com.health.pop.ui.common.StickerCard
import com.health.pop.ui.common.popHeroBubbles
import com.health.pop.ui.common.rememberPopPalette
import com.health.pop.ui.onboarding.LocalOnboarding
import kotlinx.coroutines.delay

private const val TIP_AI = "ai"
private const val DISCLAIMER = "Gợi ý chỉ để bạn tự theo dõi, không phải chẩn đoán hay tư vấn y khoa."

// Chỉ hiện bộ đếm ký tự khi câu hỏi sắp chạm giới hạn của server.
private const val COUNTER_THRESHOLD = ValidateAiPromptUseCase.MAX_PROMPT_LENGTH * 4 / 5

private data class Suggestion(val emoji: String, val label: String, val prompt: String)

private const val HOMEWORK_DISCLAIMER =
    "Giáo sư POP là AI nên có thể nhầm. Hãy đối chiếu với sách giáo khoa hoặc hỏi thầy cô khi cần."

private val HOMEWORK_SUGGESTIONS = listOf(
    Suggestion("➗", "Cách giải phương trình bậc hai", "Giải thích giúp mình cách giải phương trình bậc hai, kèm một ví dụ từng bước"),
    Suggestion("📖", "Các bước viết đoạn văn nghị luận", "Hướng dẫn mình các bước viết một đoạn văn nghị luận xã hội"),
    Suggestion("🔬", "Hiểu bài quang hợp", "Giải thích quá trình quang hợp thật dễ hiểu cho học sinh lớp 8")
)

private val SUGGESTIONS = listOf(
    Suggestion("📊", "Phân tích tuần qua của mình", ValidateAiPromptUseCase.DEFAULT_PROMPT),
    Suggestion("📱", "Cách giảm lướt Shorts khi học bài", "Cho mình 2 cách giảm lướt Shorts khi học bài"),
    Suggestion("⏱️", "Kế hoạch tập trung 25 phút hôm nay", "Gợi ý cho mình một kế hoạch tập trung 25 phút hôm nay")
)

/** Một hàng trong khung chat: tiêu đề ngày, tin nhắn hoặc hiệu ứng "POP-AI đang trả lời". */
private sealed interface ChatRow {
    val key: String

    data class Day(val label: String, val dayKey: Int) : ChatRow {
        override val key: String = "day_$dayKey"
    }

    data class Msg(val message: ChatMessage, val canRetry: Boolean) : ChatRow {
        override val key: String = message.id
    }

    data object Typing : ChatRow {
        override val key: String = "typing"
    }
}

private fun buildRows(messages: List<ChatMessage>, sending: Boolean, now: Long): List<ChatRow> {
    val rows = ArrayList<ChatRow>(messages.size + 4)
    var lastDay = -1
    messages.forEachIndexed { index, message ->
        val day = ChatTimeFormat.dayKey(message.createdAt)
        if (day != lastDay) {
            rows += ChatRow.Day(ChatTimeFormat.dayLabel(message.createdAt, now), day)
            lastDay = day
        }
        rows += ChatRow.Msg(
            message = message,
            // Chỉ cho thử lại ở tin báo lỗi cuối cùng và khi không có yêu cầu nào đang chạy.
            canRetry = message.isError && index == messages.lastIndex && !sending
        )
    }
    if (sending) rows += ChatRow.Typing
    return rows
}

/**
 * Tab "POP-AI Trợ lý học tập" theo kiểu cuộc hội thoại: bong bóng chat, ô nhập cố định phía dưới.
 * Lịch sử được AIViewModel lưu trong bộ nhớ app nên còn nguyên khi mở lại.
 *
 * Màn này tự vẽ nền xanh dưới status bar (AppNavHost chỉ chừa chỗ cho thanh điều hướng dưới) và tự xử lý bàn phím.
 */
@Composable
fun POPAIChatScreen(
    viewModel: AIViewModel,
    mode: AiMode = AiMode.ADVISOR,
    /** Có giá trị khi màn được mở từ tab khác (ví dụ Học tập): hiện nút quay lại ở đầu trang. */
    onBack: (() -> Unit)? = null
) {
    val homework = mode == AiMode.HOMEWORK
    val messages by viewModel.messages.collectAsState()
    val historyLoaded by viewModel.historyLoaded.collectAsState()
    val isSending by viewModel.isSending.collectAsState()
    val promptInput by viewModel.promptInput.collectAsState()
    val includeReflection by viewModel.includeReflection.collectAsState()
    val planActionMessage by viewModel.planActionMessage.collectAsState()
    val savingStep by viewModel.savingStep.collectAsState()

    val c = rememberPopPalette()
    val onboarding = LocalOnboarding.current
    val showTip = !homework &&
        onboarding != null &&
        !onboarding.isTipDismissed(TIP_AI) &&
        !onboarding.isTourVisible
    var showClearDialog by remember { mutableStateOf(false) }

    PopLightStatusBarIcons()

    val rows = remember(messages, isSending) { buildRows(messages, isSending, System.currentTimeMillis()) }
    val mood = if (!isSending && messages.lastOrNull()?.isError == true) PopMood.Oops else PopMood.Happy

    Column(
        Modifier
            .fillMaxSize()
            .background(c.card)
            .imePadding()
    ) {
        ChatHeader(
            c = c,
            mode = mode,
            onBack = onBack,
            mood = mood,
            thinking = isSending,
            canClear = messages.isNotEmpty(),
            onClear = { showClearDialog = true }
        )
        ChatMessageList(
            modifier = Modifier.weight(1f),
            c = c,
            mode = mode,
            rows = rows,
            loaded = historyLoaded,
            showTip = showTip,
            savingStep = savingStep,
            onDismissTip = { onboarding?.dismissTip(TIP_AI) },
            onSuggestion = viewModel::sendSuggestion,
            onAddStep = viewModel::addStepToPlan,
            onRetry = viewModel::retry
        )
        ChatInputBar(
            c = c,
            mode = mode,
            text = promptInput,
            onTextChange = viewModel::onPromptChange,
            includeReflection = includeReflection,
            onReflectionChange = { viewModel.includeReflection.value = it },
            canSend = promptInput.isNotBlank() && !isSending,
            onSend = viewModel::send,
            notice = planActionMessage,
            onDismissNotice = viewModel::dismissPlanMessage
        )
    }

    if (showClearDialog) {
        ClearHistoryDialog(
            c = c,
            assistantName = if (homework) "Giáo sư POP" else "POP-AI",
            onConfirm = {
                showClearDialog = false
                viewModel.clearHistory()
            },
            onDismiss = { showClearDialog = false }
        )
    }
}

/* ───────────────────────── Đầu trang ───────────────────────── */

@Composable
private fun ChatHeader(
    c: PopPalette,
    mode: AiMode,
    onBack: (() -> Unit)?,
    mood: PopMood,
    thinking: Boolean,
    canClear: Boolean,
    onClear: () -> Unit
) {
    val shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.heroTop, c.heroBottom)))
            .popHeroBubbles()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val homework = mode == AiMode.HOMEWORK
        if (onBack != null) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
                    .clickable(onClickLabel = "Quay lại", role = Role.Button, onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                PopText("‹", Color.White, 28, weight = FontWeight.Black)
            }
            Spacer(Modifier.width(8.dp))
        }
        PopMascot(mood, Modifier.size(56.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            PopText(
                if (homework) "POP AI  ·  HỌC TẬP" else "POP AI  ·  TRỢ LÝ", Color.White.copy(alpha = 0.85f), 11,
                weight = FontWeight.Bold, spacing = 1.1f, maxLines = 1
            )
            Spacer(Modifier.height(2.dp))
            PopText(
                if (homework) "AI Giáo sư bài tập" else "POP-AI Trợ lý học tập", Color.White, 19,
                weight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            PopText(
                when {
                    thinking -> if (homework) "Giáo sư đang suy nghĩ…" else "Đang suy nghĩ cho bạn…"
                    homework -> "Hỏi bài, mình giải thích từng bước 🎓"
                    else -> "Gợi ý hành động nhỏ, không phán xét 🌟"
                },
                Color.White.copy(alpha = 0.9f), 12,
                weight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        if (canClear) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.22f))
                    .clickable(onClickLabel = "Xóa lịch sử trò chuyện", role = Role.Button, onClick = onClear),
                contentAlignment = Alignment.Center
            ) {
                PopText("🗑️", Color.White, 18)
            }
        }
    }
}

/* ───────────────────────── Danh sách tin nhắn ───────────────────────── */

@Composable
private fun ChatMessageList(
    modifier: Modifier,
    c: PopPalette,
    mode: AiMode,
    rows: List<ChatRow>,
    loaded: Boolean,
    showTip: Boolean,
    savingStep: SavingStep?,
    onDismissTip: () -> Unit,
    onSuggestion: (String) -> Unit,
    onAddStep: (String, String) -> Unit,
    onRetry: (String) -> Unit
) {
    val listState = rememberLazyListState()
    // Số mục đứng trước các hàng chat (dòng lưu ý + thẻ hướng dẫn) để tính đúng vị trí cuộn.
    val leadingItems = 1 + if (showTip) 1 else 0
    val lastIndex = leadingItems + rows.size - 1
    val lastKey = rows.lastOrNull()?.key
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    var scrolledOnce by remember { mutableStateOf(false) }

    // Lần đầu nhảy thẳng xuống cuối; sau đó cuộn mượt mỗi khi có tin mới, hiệu ứng "đang trả lời" hoặc mở bàn phím.
    LaunchedEffect(lastKey, loaded, keyboardOpen) {
        if (!loaded || rows.isEmpty()) return@LaunchedEffect
        if (scrolledOnce) {
            listState.animateScrollToItem(lastIndex)
        } else {
            listState.scrollToItem(lastIndex)
            scrolledOnce = true
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "note") {
            PopText(
                if (mode == AiMode.HOMEWORK) HOMEWORK_DISCLAIMER else DISCLAIMER,
                c.textSoft, 12, Modifier.fillMaxWidth(), align = TextAlign.Center
            )
        }
        if (showTip) {
            item(key = "tip") { AiTip(c = c, onDismiss = onDismissTip) }
        }
        if (rows.isEmpty() && loaded) {
            item(key = "welcome") { WelcomeCard(c = c, mode = mode, onSuggestion = onSuggestion) }
        }
        items(rows, key = { it.key }) { row ->
            when (row) {
                is ChatRow.Day -> DayHeader(row.label, c)
                is ChatRow.Msg ->
                    if (row.message.role == ChatRole.USER) {
                        UserBubble(row.message, c)
                    } else {
                        AssistantBubble(
                            message = row.message,
                            canRetry = row.canRetry,
                            savingStep = savingStep,
                            c = c,
                            onAddStep = onAddStep,
                            onRetry = onRetry
                        )
                    }
                ChatRow.Typing -> TypingBubble(c)
            }
        }
    }
}

@Composable
private fun DayHeader(label: String, c: PopPalette) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        PopText(
            label, c.textSoft, 12,
            Modifier
                .clip(CircleShape)
                .background(c.field)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            FontWeight.Bold
        )
    }
}

/** Bong bóng kiểu sticker: viền đậm + bóng cứng bên dưới (cùng cách vẽ với StickerCard). */
@Composable
private fun BubbleSurface(
    shape: Shape,
    fill: Color,
    borderColor: Color,
    c: PopPalette,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(modifier.padding(bottom = 3.dp)) {
        Box(
            Modifier
                .matchParentSize()
                .offset(y = 3.dp)
                .clip(shape)
                .background(c.outline)
        )
        Column(
            Modifier
                .clip(shape)
                .background(fill)
                .border(2.dp, borderColor, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content
        )
    }
}

@Composable
private fun UserBubble(message: ChatMessage, c: PopPalette) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 6.dp, bottomStart = 20.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
        BubbleSurface(shape, PopAzure, c.outline, c, Modifier.widthIn(max = 300.dp)) {
            SelectionContainer {
                PopText(message.text, Color.White, 15, weight = FontWeight.SemiBold)
            }
        }
        val meta = ChatTimeFormat.time(message.createdAt) +
            if (message.withReflection) "  ·  📓 kèm nhật ký" else ""
        PopText(meta, c.textSoft, 11, Modifier.padding(top = 2.dp, end = 6.dp))
    }
}

@Composable
private fun AssistantBubble(
    message: ChatMessage,
    canRetry: Boolean,
    savingStep: SavingStep?,
    c: PopPalette,
    onAddStep: (String, String) -> Unit,
    onRetry: (String) -> Unit
) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    val isError = message.isError
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        PopBadge(if (isError) "😕" else "🤖", if (isError) PopCoral else PopSun, c, size = 34.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f, fill = false)) {
            BubbleSurface(
                shape = shape,
                fill = if (isError) PopCoral.copy(alpha = 0.12f).compositeOver(c.card) else c.field,
                borderColor = if (isError) PopCoral else c.outline,
                c = c,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                if (message.text.isNotBlank()) {
                    SelectionContainer {
                        PopText(message.text, c.text, 15, weight = FontWeight.Medium)
                    }
                }
                if (message.reflectionMissing) {
                    PopText(
                        "Hôm nay bạn chưa có nhật ký nên POP-AI chưa dùng được phần phản tư.",
                        c.textSoft,
                        12
                    )
                }
                if (message.steps.isNotEmpty()) {
                    PopText(
                        "Hành động khuyến nghị (bấm “Thêm” để đưa vào kế hoạch):",
                        c.accent,
                        13,
                        weight = FontWeight.ExtraBold
                    )
                    message.steps.forEach { step ->
                        val added = step in message.addedSteps
                        val saving = savingStep != null &&
                            savingStep.messageId == message.id &&
                            savingStep.step == step
                        StepRow(
                            step = step,
                            added = added,
                            saving = saving,
                            enabled = !added && savingStep == null,
                            c = c,
                            onAdd = { onAddStep(message.id, step) }
                        )
                    }
                }
                if (isError && canRetry) {
                    PopPillButton(
                        text = "↻  Thử lại",
                        c = c,
                        onClick = { onRetry(message.id) }
                    )
                }
            }
            PopText(
                ChatTimeFormat.time(message.createdAt),
                c.textSoft,
                11,
                Modifier.padding(top = 2.dp, start = 6.dp)
            )
        }
    }
}

@Composable
private fun TypingBubble(c: PopPalette) {
    val shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        PopBadge("🤖", PopSun, c, size = 34.dp)
        Spacer(Modifier.width(8.dp))
        BubbleSurface(shape = shape, fill = c.field, borderColor = c.outline, c = c) {
            PopLoadingDots(c.accent, Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun StepRow(
    step: String,
    added: Boolean,
    saving: Boolean,
    enabled: Boolean,
    c: PopPalette,
    onAdd: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(1.5.dp, c.fieldLine, shape)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PopText(step, c.text, 14, Modifier.weight(1f), FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        PopPillButton(
            text = when {
                saving -> "Đang lưu…"
                added -> "✓ Đã thêm"
                else -> "+ Thêm"
            },
            c = c,
            onClick = onAdd,
            enabled = enabled,
            fill = if (added) PopMint else PopSun
        )
    }
}

/* ───────────────────────── Màn chào & hướng dẫn ───────────────────────── */

@Composable
private fun WelcomeCard(c: PopPalette, mode: AiMode, onSuggestion: (String) -> Unit) {
    val homework = mode == AiMode.HOMEWORK
    StickerCard(c, fill = c.field, contentPadding = 18.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PopBadge(if (homework) "🎓" else "👋", PopSun, c, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            PopText(
                if (homework) "Chào bạn, mình là Giáo sư POP!" else "Chào bạn, mình là POP-AI!",
                c.text, 17, Modifier.weight(1f), FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.height(10.dp))
        PopText(
            if (homework) {
                "Gõ đề bài hoặc điều bạn chưa hiểu (ví dụ: “Giải thích định lý Pytago”). " +
                    "Mình sẽ giải thích từng bước để bạn tự làm được."
            } else {
                "Kể cho mình điều bạn đang gặp khi học (ví dụ: lướt Shorts mãi không dừng). " +
                    "Mình sẽ gợi ý vài hành động nhỏ, không phán xét."
            },
            c.text,
            14
        )
        Spacer(Modifier.height(14.dp))
        PopText("Thử hỏi nhanh:", c.accent, 13, weight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            (if (homework) HOMEWORK_SUGGESTIONS else SUGGESTIONS).forEach { suggestion ->
                SuggestionChip(suggestion, c) { onSuggestion(suggestion.prompt) }
            }
        }
    }
}

@Composable
private fun SuggestionChip(suggestion: Suggestion, c: PopPalette, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.card)
            .border(2.dp, c.fieldLine, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PopText(suggestion.emoji, c.text, 18)
        Spacer(Modifier.width(10.dp))
        PopText(suggestion.label, c.text, 14, Modifier.weight(1f), FontWeight.SemiBold)
    }
}

/** Gợi ý "màn này dùng để làm gì": hiện một lần cho mỗi tài khoản đến khi bấm "Đã hiểu". */
@Composable
private fun AiTip(c: PopPalette, onDismiss: () -> Unit) {
    StickerCard(
        c,
        fill = PopSun.copy(alpha = 0.25f).compositeOver(c.card),
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.Top) {
            PopText("💡", c.text, 18)
            Spacer(Modifier.width(10.dp))
            Column {
                PopText("Hỏi POP-AI như thế nào?", c.text, 15, weight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                PopText(
                    "Viết điều bạn đang gặp (ví dụ: “mình hay lướt Shorts khi học bài”) rồi bấm nút gửi. " +
                        "Ở mỗi gợi ý, bấm “Thêm” để đưa vào kế hoạch của bạn. " +
                        "Lịch sử trò chuyện được lưu trên máy này; bấm biểu tượng thùng rác ở đầu trang để xóa.",
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

/* ───────────────────────── Thanh nhập ───────────────────────── */

@Composable
private fun ChatInputBar(
    c: PopPalette,
    mode: AiMode,
    text: String,
    onTextChange: (String) -> Unit,
    includeReflection: Boolean,
    onReflectionChange: (Boolean) -> Unit,
    canSend: Boolean,
    onSend: () -> Unit,
    notice: String?,
    onDismissNotice: () -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.card)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(c.fieldLine)
        )
        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
        ) {
            if (notice != null) {
                NoticePill(message = notice, c = c, onDismiss = onDismissNotice)
            }
            if (mode == AiMode.ADVISOR) {
                ReflectionToggle(checked = includeReflection, c = c, onCheckedChange = onReflectionChange)
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                ComposerField(
                    text = text,
                    onTextChange = onTextChange,
                    hint = if (mode == AiMode.HOMEWORK) "Nhập đề bài hoặc câu hỏi…" else "Nhập câu hỏi cho POP-AI…",
                    c = c,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                SendButton(enabled = canSend, c = c, onClick = onSend)
            }
            if (text.length >= COUNTER_THRESHOLD) {
                PopText(
                    "${text.length}/${ValidateAiPromptUseCase.MAX_PROMPT_LENGTH}",
                    c.textSoft,
                    12,
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, end = 4.dp),
                    align = TextAlign.End
                )
            }
        }
    }
}

/** Thông báo ngắn sau khi thêm một bước vào kế hoạch; tự ẩn sau vài giây hoặc khi chạm vào. */
@Composable
private fun NoticePill(message: String, c: PopPalette, onDismiss: () -> Unit) {
    LaunchedEffect(message) {
        delay(4000)
        onDismiss()
    }
    Row(
        Modifier
            .padding(bottom = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(PopSun.copy(alpha = 0.25f).compositeOver(c.card))
            .clickable(role = Role.Button, onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PopText("📌", c.text, 16)
        Spacer(Modifier.width(8.dp))
        PopText(message, c.text, 13, Modifier.weight(1f), FontWeight.SemiBold)
    }
}

/** Hộp chọn kiểu sticker: ô vuông bo góc, tick "✓" trên nền xanh khi được chọn. */
@Composable
private fun ReflectionToggle(
    checked: Boolean,
    c: PopPalette,
    onCheckedChange: (Boolean) -> Unit
) {
    val boxShape = RoundedCornerShape(7.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(
                value = checked,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(24.dp)
                .clip(boxShape)
                .background(if (checked) PopAzure else c.card)
                .border(2.dp, if (checked) c.outline else c.fieldLine, boxShape),
            contentAlignment = Alignment.Center
        ) {
            if (checked) PopText("✓", Color.White, 14, weight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        PopText(
            "Đồng ý đính kèm nội dung phản tư nhật ký hôm nay",
            c.text,
            13,
            Modifier.weight(1f),
            FontWeight.SemiBold
        )
    }
}

@Composable
private fun ComposerField(
    text: String,
    onTextChange: (String) -> Unit,
    hint: String,
    c: PopPalette,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(24.dp)
    val source = remember { MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    val line by animateColorAsState(if (focused) c.accent else c.fieldLine, tween(160), label = "composerLine")
    BasicTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier,
        textStyle = TextStyle(
            color = c.text,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = PopFont
        ),
        cursorBrush = SolidColor(c.accent),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            imeAction = ImeAction.Default
        ),
        minLines = 1,
        maxLines = 5,
        interactionSource = source,
        decorationBox = { inner ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(shape)
                    .background(c.field)
                    .border(2.dp, line, shape)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) PopText(hint, c.text.copy(alpha = 0.38f), 16)
                inner()
            }
        }
    )
}

@Composable
private fun SendButton(enabled: Boolean, c: PopPalette, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .clip(CircleShape)
            .background(PopAzure)
            .border(2.dp, c.outline, CircleShape)
            .clickable(enabled = enabled, onClickLabel = "Gửi câu hỏi", role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        PopText("↑", Color.White, 24, weight = FontWeight.Black)
    }
}

/* ───────────────────────── Hộp thoại xác nhận xoá ───────────────────────── */

@Composable
private fun ClearHistoryDialog(c: PopPalette, assistantName: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        StickerCard(c, fill = c.card, contentPadding = 20.dp) {
            PopText("Xóa lịch sử trò chuyện?", c.text, 18, weight = FontWeight.ExtraBold)
            Spacer(Modifier.height(8.dp))
            PopText(
                "Toàn bộ tin nhắn với $assistantName trên máy này sẽ bị xóa và không thể khôi phục." +
                    if (assistantName == "POP-AI") " Các nhiệm vụ đã thêm vào kế hoạch vẫn được giữ nguyên." else "",
                c.text,
                14
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
            ) {
                PopPillButton(text = "Hủy", c = c, onClick = onDismiss, fill = Color.White)
                PopPillButton(text = "Xóa", c = c, onClick = onConfirm, fill = PopCoral)
            }
        }
    }
}

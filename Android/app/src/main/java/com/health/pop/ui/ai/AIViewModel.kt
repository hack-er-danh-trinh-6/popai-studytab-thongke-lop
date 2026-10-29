package com.health.pop.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.health.pop.core.network.ApiResult
import com.health.pop.data.local.ChatHistoryStore
import com.health.pop.data.local.InMemoryChatHistoryStore
import com.health.pop.data.repository.AIRepository
import com.health.pop.domain.model.AIAdvice
import com.health.pop.domain.model.AiMode
import com.health.pop.domain.model.ChatMessage
import com.health.pop.domain.model.ChatRole
import com.health.pop.domain.usecase.ChatHistoryRules
import com.health.pop.domain.usecase.ValidateAiPromptUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID

/** Bước gợi ý đang được lưu vào kế hoạch: xác định bằng tin nhắn chứa nó và nội dung bước. */
data class SavingStep(val messageId: String, val step: String)

/**
 * Cuộc trò chuyện với POP-AI. Tin nhắn được lưu qua [historyStore] (đọc/ghi ở luồng nền) nên còn nguyên
 * sau khi đóng và mở lại app.
 */
class AIViewModel(
    private val repository: AIRepository,
    private val historyStore: ChatHistoryStore = InMemoryChatHistoryStore(),
    private val promptValidator: ValidateAiPromptUseCase = ValidateAiPromptUseCase(),
    private val mode: AiMode = AiMode.ADVISOR
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    /** false cho đến khi đọc xong lịch sử, để không nháy màn hình chào khi người dùng đã có lịch sử. */
    private val _historyLoaded = MutableStateFlow(false)
    val historyLoaded: StateFlow<Boolean> = _historyLoaded.asStateFlow()

    /** Đang chờ POP-AI trả lời. */
    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _planActionMessage = MutableStateFlow<String?>(null)
    val planActionMessage: StateFlow<String?> = _planActionMessage.asStateFlow()

    private val _savingStep = MutableStateFlow<SavingStep?>(null)
    val savingStep: StateFlow<SavingStep?> = _savingStep.asStateFlow()

    var promptInput = MutableStateFlow("")
    var includeReflection = MutableStateFlow(false)

    private val saveMutex = Mutex()

    // Tăng mỗi khi xoá lịch sử để bỏ qua câu trả lời của yêu cầu đang bay (đã thuộc cuộc trò chuyện cũ).
    private var epoch = 0

    private val loadJob = viewModelScope.launch {
        val stored = withContext(Dispatchers.IO) {
            try {
                historyStore.load()
            } catch (_: Exception) {
                emptyList<ChatMessage>()
            }
        }
        val restored = ChatHistoryRules.recoverInterrupted(ChatHistoryRules.trim(stored), System.currentTimeMillis())
        // Tin nhắn gõ trước khi lịch sử tải xong (hiếm) được giữ lại, xếp sau lịch sử cũ.
        _messages.update { current -> ChatHistoryRules.trim(restored + current) }
        _historyLoaded.value = true
    }

    fun onPromptChange(value: String) {
        promptInput.value = promptValidator.limit(value)
    }

    /** Gửi nội dung đang gõ trong ô nhập. */
    fun send() {
        if (_isSending.value) return
        val text = promptValidator.limit(promptInput.value).trim()
        if (text.isEmpty()) return
        promptInput.value = ""
        submit(text, includeReflection.value)
    }

    /** Gửi một câu hỏi gợi ý có sẵn (nút gợi ý ở màn chào). */
    fun sendSuggestion(text: String) {
        if (_isSending.value) return
        val clean = promptValidator.limit(text).trim()
        if (clean.isEmpty()) return
        submit(clean, includeReflection.value)
    }

    /** Gửi lại câu hỏi của tin báo lỗi cuối cùng (không tạo thêm tin nhắn người dùng trùng lặp). */
    fun retry(errorMessageId: String) {
        if (_isSending.value) return
        val current = _messages.value
        if (current.lastOrNull()?.id != errorMessageId) return
        val userMessage = ChatHistoryRules.userMessageBefore(current, errorMessageId) ?: return
        _planActionMessage.value = null
        mutate { list -> list.filterNot { it.id == errorMessageId } }
        requestReply(userMessage)
    }

    fun addStepToPlan(messageId: String, step: String) {
        val message = _messages.value.firstOrNull { it.id == messageId } ?: return
        if (step in message.addedSteps || _savingStep.value != null) return
        _planActionMessage.value = null
        _savingStep.value = SavingStep(messageId, step)
        viewModelScope.launch {
            try {
                val notice = when (val result = repository.addActionToPlan(step)) {
                    is ApiResult.Success -> {
                        mutate { list ->
                            list.map { if (it.id == messageId) it.copy(addedSteps = it.addedSteps + step) else it }
                        }
                        "Đã lưu vào kế hoạch cá nhân."
                    }
                    is ApiResult.Error -> result.message
                    else -> "Không thể lưu nhiệm vụ vào kế hoạch."
                }
                _planActionMessage.value = notice
            } finally {
                _savingStep.value = null
            }
        }
    }

    fun dismissPlanMessage() {
        _planActionMessage.value = null
    }

    /** Xoá toàn bộ lịch sử trò chuyện (cả trong bộ nhớ app). */
    fun clearHistory() {
        epoch++
        _isSending.value = false
        _planActionMessage.value = null
        mutate { emptyList() }
    }

    private fun submit(text: String, withReflection: Boolean) {
        _planActionMessage.value = null
        val userMessage = ChatMessage(
            id = newId(),
            role = ChatRole.USER,
            text = text,
            createdAt = System.currentTimeMillis(),
            withReflection = withReflection
        )
        mutate { it + userMessage }
        requestReply(userMessage)
    }

    private fun requestReply(userMessage: ChatMessage) {
        val requestEpoch = epoch
        _isSending.value = true
        // Vài lượt trước câu hỏi này giúp POP-AI hiểu các câu hỏi nối tiếp.
        val context = ChatHistoryRules.contextBefore(_messages.value, userMessage.id)
        viewModelScope.launch {
            try {
                val result = repository.getAdvice(userMessage.text, userMessage.withReflection, context, mode)
                val reply = toReply(result, userMessage)
                if (requestEpoch == epoch) mutate { it + reply }
            } finally {
                if (requestEpoch == epoch) _isSending.value = false
            }
        }
    }

    private fun toReply(result: ApiResult<AIAdvice>, userMessage: ChatMessage): ChatMessage {
        val now = System.currentTimeMillis()
        return when (result) {
            is ApiResult.Success -> ChatMessage(
                id = newId(),
                role = ChatRole.ASSISTANT,
                text = result.data.adviceText,
                createdAt = now,
                steps = result.data.actionableSteps,
                // Người dùng bật đính kèm nhật ký nhưng server không dùng được (hôm nay chưa có nhật ký).
                reflectionMissing = userMessage.withReflection && !result.data.reflectionUsed
            )
            is ApiResult.Error -> errorReply(result.message, now)
            is ApiResult.ServerUnconfigured -> errorReply("Máy chủ chưa sẵn sàng.", now)
            is ApiResult.NetworkError ->
                errorReply("Không thể kết nối mạng. Vui lòng kiểm tra kết nối và thử lại.", now)
        }
    }

    private fun errorReply(message: String, now: Long) = ChatMessage(
        id = newId(),
        role = ChatRole.ASSISTANT,
        text = message,
        createdAt = now,
        isError = true
    )

    /** Cập nhật danh sách tin nhắn rồi lưu vào bộ nhớ app. */
    private fun mutate(transform: (List<ChatMessage>) -> List<ChatMessage>) {
        _messages.update { current -> ChatHistoryRules.trim(transform(current)) }
        persist()
    }

    /**
     * Ghi trạng thái MỚI NHẤT (không phải bản chụp lúc gọi) sau khi lịch sử cũ đã tải xong, nên không bao giờ ghi đè
     * lịch sử cũ bằng danh sách thiếu. Mutex giữ đúng thứ tự ghi; NonCancellable để lần ghi cuối không bị bỏ dở
     * khi ViewModel bị huỷ.
     */
    private fun persist() {
        viewModelScope.launch {
            loadJob.join()
            saveMutex.withLock {
                val latest = _messages.value
                withContext(NonCancellable + Dispatchers.IO) {
                    try {
                        if (latest.isEmpty()) historyStore.clear() else historyStore.save(latest)
                    } catch (_: Exception) {
                        // Không lưu được thì cuộc trò chuyện đang diễn ra vẫn dùng bình thường.
                    }
                }
            }
        }
    }

    private fun newId(): String = UUID.randomUUID().toString()
}

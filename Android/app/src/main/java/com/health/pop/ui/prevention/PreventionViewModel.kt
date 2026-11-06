package com.health.pop.ui.prevention

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.health.pop.core.network.ApiResult
import com.health.pop.data.repository.AssessmentRepository
import com.health.pop.domain.model.AssessmentGroup
import com.health.pop.domain.model.AssessmentQuestion
import com.health.pop.domain.model.AssessmentPhase
import com.health.pop.domain.model.AssessmentResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update

sealed class AssessmentSubmissionState {
    object Idle : AssessmentSubmissionState()
    object Submitting : AssessmentSubmissionState()
    data class Success(val result: AssessmentResult, val isDemoData: Boolean) : AssessmentSubmissionState()
    data class Error(val message: String) : AssessmentSubmissionState()
}

class PreventionViewModel(private val repository: AssessmentRepository, val phase: AssessmentPhase = AssessmentPhase.PRE) : ViewModel() {

    val questions: List<AssessmentQuestion> = repository.getQuestions(phase)
    private val _answers = MutableStateFlow<Map<String, Int>>(emptyMap())
    val answers: StateFlow<Map<String, Int>> = _answers.asStateFlow()
    private val _submissionState = MutableStateFlow<AssessmentSubmissionState>(
        repository.latestSubmission.value?.let { AssessmentSubmissionState.Success(it, false) }
            ?: AssessmentSubmissionState.Idle
    )
    val submissionState: StateFlow<AssessmentSubmissionState> = _submissionState.asStateFlow()
    private val _historyState = MutableStateFlow<List<AssessmentResult>>(emptyList())
    val historyState: StateFlow<List<AssessmentResult>> = _historyState.asStateFlow()
    private val _historyLoading = MutableStateFlow(false)
    val historyLoading: StateFlow<Boolean> = _historyLoading.asStateFlow()
    private val _historyLoaded = MutableStateFlow(false)
    val historyLoaded: StateFlow<Boolean> = _historyLoaded.asStateFlow()
    private val _historyError = MutableStateFlow<String?>(null)
    val historyError: StateFlow<String?> = _historyError.asStateFlow()
    private val _profile = MutableStateFlow<Map<String, String>>(emptyMap())
    val profile: StateFlow<Map<String, String>> = _profile.asStateFlow()
    private val _openAnswers = MutableStateFlow<Map<String, String>>(emptyMap())
    val openAnswers: StateFlow<Map<String, String>> = _openAnswers.asStateFlow()

    init {
        loadHistory()
    }

    fun loadHistory() {
        if (_historyLoading.value) return
        viewModelScope.launch {
            _historyLoading.value = true
            _historyLoaded.value = false
            _historyError.value = null
            when (val result = repository.getAssessmentHistory()) {
                is ApiResult.Success -> {
                    _historyState.value = result.data
                    _historyLoaded.value = true
                }
                is ApiResult.Error -> _historyError.value = result.message
                ApiResult.NetworkError -> _historyError.value = "Không kết nối được máy chủ để kiểm tra các phiếu đã làm."
                ApiResult.ServerUnconfigured -> _historyError.value = "Máy chủ chưa sẵn sàng để kiểm tra các phiếu đã làm."
            }
            _historyLoading.value = false
        }
    }

    fun setAnswer(questionId: String, score: Int) {
        _answers.update { current -> current + (questionId to score) }
    }

    fun setProfile(key: String, value: String) { _profile.value = _profile.value + (key to value) }
    fun setOpenAnswer(key: String, value: String) { _openAnswers.value = _openAnswers.value + (key to value) }

    fun submitAssessment() {
        val applicableQuestions = if (phase == AssessmentPhase.POST && _profile.value["studyGroup"] != "Nhóm thực nghiệm") {
            questions.filter { it.group != AssessmentGroup.DG }
        } else {
            questions
        }
        val applicableIds = applicableQuestions.map { it.id }.toSet()
        val applicableAnswers = _answers.value.filterKeys { it in applicableIds }
        if (applicableAnswers.size < applicableQuestions.size) {
            _submissionState.value = AssessmentSubmissionState.Error("Vui lòng trả lời đủ ${applicableQuestions.size} câu hỏi trước khi gửi.")
            return
        }
        _submissionState.value = AssessmentSubmissionState.Submitting
        viewModelScope.launch {
            when (val result = repository.submitAssessment(phase, applicableAnswers, _profile.value, _openAnswers.value)) {
                is ApiResult.Success -> {
                    _submissionState.value = AssessmentSubmissionState.Success(result.data, result.isDemoData)
                    _historyState.update { current ->
                        listOf(result.data) + current.filterNot { it.phase == result.data.phase }
                    }
                    loadHistory()
                }
                is ApiResult.Error -> _submissionState.value = AssessmentSubmissionState.Error(result.message)
                else -> _submissionState.value = AssessmentSubmissionState.Error("Máy chủ chưa cấu hình.")
            }
        }
    }

    fun markSubmissionHandled() {
        _submissionState.value = AssessmentSubmissionState.Idle
    }

    fun dismissSubmissionError() {
        if (_submissionState.value is AssessmentSubmissionState.Error) {
            _submissionState.value = AssessmentSubmissionState.Idle
        }
    }

    fun resetSubmissionState() {
        _submissionState.value = AssessmentSubmissionState.Idle
        _answers.value = emptyMap()
        repository.clearLatestSubmission()
    }
}

package com.health.pop.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.health.pop.core.network.ApiResult
import com.health.pop.data.repository.ClassroomRepository
import com.health.pop.domain.model.ClassMembership
import com.health.pop.domain.model.ClassRoster
import com.health.pop.domain.model.MembershipStatus
import com.health.pop.domain.model.TimetableEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudyUiState(
    val isLoading: Boolean = true,
    /** Lỗi khi tải trạng thái lớp (chưa biết học sinh đã vào lớp hay chưa). */
    val errorMessage: String? = null,
    val membershipLoaded: Boolean = false,
    val membership: ClassMembership = ClassMembership(),
    val roster: ClassRoster? = null,
    val rosterError: String? = null,
    val timetable: List<TimetableEntry> = emptyList(),
    val timetableError: String? = null
) {
    val inClass: Boolean get() = membership.status == MembershipStatus.APPROVED
}

/** Dữ liệu cho tab "Học tập": lớp đang tham gia, thành viên và thời khóa biểu của lớp. */
class StudyViewModel(
    private val repository: ClassroomRepository,
    /** Chỉ học sinh mới có lớp; tài khoản khác không gọi API lớp học (máy chủ sẽ trả 403). */
    private val isStudent: Boolean = true
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        if (isStudent) StudyUiState() else StudyUiState(isLoading = false, membershipLoaded = true)
    )
    val uiState: StateFlow<StudyUiState> = _uiState.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    /** Tải lại (khi mở tab, quay lại từ màn Lớp học, hoặc bấm Thử lại). */
    fun refresh() {
        if (!isStudent || job?.isActive == true) return
        job = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val membership = repository.getMembership()) {
                is ApiResult.Success -> {
                    val data = membership.data
                    if (data.status == MembershipStatus.APPROVED) {
                        loadClassDetails(data)
                    } else {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                membershipLoaded = true,
                                membership = data,
                                roster = null,
                                rosterError = null,
                                timetable = emptyList(),
                                timetableError = null
                            )
                        }
                    }
                }
                is ApiResult.Error -> _uiState.update { it.copy(isLoading = false, errorMessage = membership.message) }
                else -> _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Không thể tải thông tin lớp học.")
                }
            }
        }
    }

    private suspend fun loadClassDetails(membership: ClassMembership) = coroutineScope {
        val rosterDeferred = async { repository.getRoster() }
        val timetableDeferred = async { repository.getTimetable() }
        val roster = rosterDeferred.await()
        val timetable = timetableDeferred.await()
        _uiState.update {
            it.copy(
                isLoading = false,
                membershipLoaded = true,
                membership = membership,
                roster = (roster as? ApiResult.Success)?.data ?: it.roster,
                rosterError = (roster as? ApiResult.Error)?.message,
                timetable = (timetable as? ApiResult.Success)?.data ?: it.timetable,
                timetableError = (timetable as? ApiResult.Error)?.message
            )
        }
    }
}

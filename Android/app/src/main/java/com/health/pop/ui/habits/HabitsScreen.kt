package com.health.pop.ui.habits

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.health.pop.ui.common.PopMood
import com.health.pop.ui.common.PopPage
import com.health.pop.ui.common.rememberPopPalette
import com.health.pop.ui.journal.JournalSaveState
import com.health.pop.ui.journal.JournalViewModel
import com.health.pop.ui.plan.PlanSaveState
import com.health.pop.ui.plan.PlanViewModel

/**
 * Tab "Kế hoạch": gộp Kế hoạch và Nhật ký vào một chỗ, chuyển qua lại bằng hai nút lớn ở đầu trang.
 * Màn này tự vẽ nền xanh dưới status bar (AppNavHost chỉ chừa chỗ cho thanh điều hướng dưới).
 */
@Composable
fun HabitsScreen(
    planViewModel: PlanViewModel,
    journalViewModel: JournalViewModel,
    initialTab: HabitsTab = HabitsTab.PLAN
) {
    val plan by planViewModel.currentPlan.collectAsState()
    val planSaveState by planViewModel.saveState.collectAsState()
    val journal by journalViewModel.todayJournal.collectAsState()
    val journalSaveState by journalViewModel.saveState.collectAsState()

    var tabName by rememberSaveable { mutableStateOf(initialTab.name) }
    val tab = HabitsTab.valueOf(tabName)

    // Bản nháp được dựng lại khi dữ liệu từ máy chủ đổi (tải xong / lưu xong) và giữ nguyên khi chuyển mục.
    val planDraft = remember(plan) { PlanDraft(plan) }
    val journalDraft = remember(journal) { JournalDraft(journal) }
    var planFormError by remember { mutableStateOf<String?>(null) }
    var journalFormError by remember { mutableStateOf<String?>(null) }

    val c = rememberPopPalette()

    val planSaving = planSaveState is PlanSaveState.Saving
    val planError = (planSaveState as? PlanSaveState.Error)?.message
    val journalSaving = journalSaveState is JournalSaveState.Saving
    val journalError = (journalSaveState as? JournalSaveState.Error)?.message

    val hasError = when (tab) {
        HabitsTab.PLAN -> planError != null || planFormError != null
        HabitsTab.JOURNAL -> journalError != null || journalFormError != null
    }
    val bubble = when (tab) {
        HabitsTab.PLAN -> when {
            planSaving -> "Đang lưu kế hoạch của bạn…"
            planError != null -> "Chưa lưu được. Xem thông báo bên dưới nhé."
            planFormError != null -> "Có chỗ cần sửa lại. Xem bên dưới nhé."
            planSaveState is PlanSaveState.Success -> "Đã lưu kế hoạch rồi! 🎉"
            !plan.hasSavedPlan -> "Mình cùng tạo kế hoạch đầu tiên nhé ✨"
            else -> "Sửa xong nhớ bấm Lưu nhé 📝"
        }
        HabitsTab.JOURNAL -> when {
            journalSaving -> "Đang lưu nhật ký của bạn…"
            journalError != null -> "Chưa lưu được. Xem thông báo bên dưới nhé."
            journalFormError != null -> "Có chỗ cần sửa lại. Xem bên dưới nhé."
            journalSaveState is JournalSaveState.Success -> "Đã lưu nhật ký hôm nay rồi! 🎉"
            else -> "Nhìn lại hôm nay trong chưa đầy 1 phút nhé 📝"
        }
    }

    PopPage(
        c = c,
        label = "POP AI  ·  KẾ HOẠCH",
        title = if (tab == HabitsTab.PLAN) "Kế hoạch của tôi" else "Nhật ký hôm nay",
        bubble = bubble,
        mood = if (hasError) PopMood.Oops else PopMood.Happy
    ) {
        HabitsTabSwitch(selected = tab, onSelect = { tabName = it.name }, c = c)

        when (tab) {
            HabitsTab.PLAN -> PlanSection(
                c = c,
                draft = planDraft,
                hasSavedPlan = plan.hasSavedPlan,
                saving = planSaving,
                saveError = planError,
                formError = planFormError,
                onSave = {
                    planFormError = planDraft.validate()
                    if (planFormError == null) {
                        planViewModel.updatePlan(
                            dailyLimit = planDraft.dailyLimit.toInt(),
                            protectionHours = planDraft.protectionHours,
                            sessionDuration = planDraft.duration,
                            targetDays = planDraft.targetDays,
                            studyTasks = planDraft.tasks.toList()
                        )
                    }
                }
            )
            HabitsTab.JOURNAL -> JournalSection(
                c = c,
                draft = journalDraft,
                saving = journalSaving,
                saveError = journalError,
                formError = journalFormError,
                onSave = {
                    journalFormError = journalDraft.validate()
                    if (journalFormError == null) journalViewModel.saveJournal(journalDraft.toJournal())
                }
            )
        }
    }
}

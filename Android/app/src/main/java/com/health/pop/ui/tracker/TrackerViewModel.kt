package com.health.pop.ui.tracker

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.core.app.NotificationManagerCompat
import com.health.pop.core.tracker.BackgroundAccess
import com.health.pop.core.tracker.TrackedApp
import com.health.pop.core.tracker.UsageLimitStore
import com.health.pop.core.tracker.UsageStatsReader
import com.health.pop.core.tracker.UsageTrackerService
import com.health.pop.core.tracker.UsageTrackerWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class AppUsageUi(
    val app: TrackedApp,
    val installed: Boolean,
    val usedMinutes: Long,
    val limitMinutes: Int
) {
    val hasLimit: Boolean get() = limitMinutes > 0
    val isOver: Boolean get() = hasLimit && usedMinutes >= limitMinutes
    val progress: Float get() = if (hasLimit) (usedMinutes.toFloat() / limitMinutes).coerceIn(0f, 1f) else 0f
}

data class TrackerUiState(
    val enabled: Boolean = false,
    val hasUsageAccess: Boolean = false,
    val notificationsAllowed: Boolean = true,
    val batteryUnrestricted: Boolean = false,
    val deviceAdminActive: Boolean = false,
    val apps: List<AppUsageUi> = emptyList()
)

class TrackerViewModel(context: Context) : ViewModel() {

    private val appContext = context.applicationContext
    private val store = UsageLimitStore(appContext)

    private val _uiState = MutableStateFlow(TrackerUiState())
    val uiState: StateFlow<TrackerUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /** Gọi khi màn hình hiện lại (ví dụ sau khi người dùng cấp quyền ở Cài đặt) và định kỳ khi đang mở. */
    fun refresh() {
        val hasAccess = UsageStatsReader.hasUsageAccess(appContext)
        val used = if (hasAccess) {
            try {
                UsageStatsReader.readToday(appContext).minutesToday
            } catch (_: SecurityException) {
                emptyMap()
            }
        } else {
            emptyMap()
        }
        _uiState.value = TrackerUiState(
            enabled = store.trackingEnabled,
            hasUsageAccess = hasAccess,
            notificationsAllowed = NotificationManagerCompat.from(appContext).areNotificationsEnabled(),
            batteryUnrestricted = BackgroundAccess.isIgnoringBatteryOptimizations(appContext),
            deviceAdminActive = BackgroundAccess.isDeviceAdminActive(appContext),
            apps = TrackedApp.entries.map { app ->
                AppUsageUi(
                    app = app,
                    installed = UsageStatsReader.isInstalled(appContext, app),
                    usedMinutes = used[app] ?: 0L,
                    limitMinutes = store.getLimitMinutes(app)
                )
            }
        )
        if (store.trackingEnabled && hasAccess) {
            UsageTrackerService.startIfNeeded(appContext)
            UsageTrackerWorker.schedule(appContext)
        }
    }

    fun setEnabled(enabled: Boolean) {
        store.trackingEnabled = enabled
        if (enabled) {
            UsageTrackerWorker.schedule(appContext)
            UsageTrackerService.startIfNeeded(appContext)
        } else {
            UsageTrackerService.stop(appContext)
            UsageTrackerWorker.cancel(appContext)
        }
        _uiState.update { it.copy(enabled = enabled) }
    }

    fun setLimit(app: TrackedApp, minutes: Int) {
        store.setLimitMinutes(app, minutes)
        // Đổi hạn mức thì cho phép báo lại các mốc của ngày hôm nay.
        store.markNotified(app, 0)
        _uiState.update { state ->
            state.copy(apps = state.apps.map { if (it.app == app) it.copy(limitMinutes = minutes) else it })
        }
    }
}

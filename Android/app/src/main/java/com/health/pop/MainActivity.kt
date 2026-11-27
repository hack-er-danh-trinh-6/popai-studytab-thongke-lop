package com.health.pop

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.compose.rememberNavController
import com.health.pop.core.designsystem.theme.POPAITheme
import com.health.pop.core.focussound.FocusSoundService
import com.health.pop.core.notification.ClassNotificationHelper
import com.health.pop.core.notification.ClassNotificationSync
import com.health.pop.core.notification.ClassNotificationWorker
import com.health.pop.core.notification.UpdateNotificationHelper
import com.health.pop.core.security.TokenManager
import com.health.pop.core.tracker.UsageNotificationHelper
import com.health.pop.core.tracker.UsageTrackerService
import com.health.pop.core.tracker.UsageTrackerWorker
import com.health.pop.data.repository.AIRepository
import com.health.pop.data.repository.AppUpdateRepository
import com.health.pop.data.repository.AssessmentRepository
import com.health.pop.data.repository.AuthRepository
import com.health.pop.data.repository.ClassroomRepository
import com.health.pop.data.repository.FocusRepository
import com.health.pop.data.repository.JournalRepository
import com.health.pop.data.repository.PlanRepository
import com.health.pop.data.repository.ProgressRepository
import com.health.pop.core.network.ApiResult
import com.health.pop.data.remote.AppVersionDto
import com.health.pop.ui.navigation.AppNavHost
import com.health.pop.domain.model.FocusSessionState
import com.health.pop.ui.focus.FocusOverlayService
import com.example.pop.ui.splash.PopSplashScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    // Đặt true khi người dùng chạm vào thông báo của giáo viên để mở màn Lớp học.
    private var openClassroomRequest by mutableStateOf(false)
    private var returnToFocusRequest by mutableStateOf(false)
    private var openTrackerRequest by mutableStateOf(false)
    private lateinit var focusRepository: FocusRepository
    private lateinit var appUpdateRepository: AppUpdateRepository

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* không cần xử lý thêm */ }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(ClassNotificationHelper.EXTRA_OPEN_CLASSROOM, false)) {
            openClassroomRequest = true
        }
        if (intent.getBooleanExtra(EXTRA_RETURN_TO_FOCUS, false)) returnToFocusRequest = true
        if (intent.getBooleanExtra(UsageNotificationHelper.EXTRA_OPEN_TRACKER, false)) openTrackerRequest = true
    }

    override fun onStart() {
        super.onStart()
        // Khởi động lại dịch vụ theo dõi nếu hệ thống đã tắt nó (chỉ làm được khi app đang ở foreground).
        UsageTrackerService.startIfNeeded(this)
        FocusOverlayService.hide(this)
        if (::focusRepository.isInitialized &&
            focusRepository.currentSession.value.state == FocusSessionState.RUNNING &&
            Settings.canDrawOverlays(this)
        ) {
            FocusOverlayService.prepare(this)
        }
    }

    override fun onStop() {
        super.onStop()
        if (::focusRepository.isInitialized &&
            focusRepository.currentSession.value.state == FocusSessionState.RUNNING &&
            Settings.canDrawOverlays(this)
        ) {
            FocusOverlayService.show(
                this,
                focusRepository.currentSession.value.taskName,
                focusRepository.currentSession.value.targetMinutes,
            )
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openClassroomRequest = intent?.getBooleanExtra(ClassNotificationHelper.EXTRA_OPEN_CLASSROOM, false) == true
        returnToFocusRequest = intent?.getBooleanExtra(EXTRA_RETURN_TO_FOCUS, false) == true
        openTrackerRequest = intent?.getBooleanExtra(UsageNotificationHelper.EXTRA_OPEN_TRACKER, false) == true

        val tokenManager = TokenManager(this)
        val authRepository = AuthRepository(tokenManager)
        val assessmentRepository = AssessmentRepository(tokenManager)
        val planRepository = PlanRepository(tokenManager)
        focusRepository = FocusRepository(tokenManager)
        appUpdateRepository = AppUpdateRepository(tokenManager)
        val journalRepository = JournalRepository(tokenManager)
        val progressRepository = ProgressRepository(tokenManager)
        val aiRepository = AIRepository(tokenManager, planRepository)
        val classroomRepository = ClassroomRepository(tokenManager)

        lifecycleScope.launch {
            focusRepository.currentSession.collect { session ->
                if (session.state == FocusSessionState.RUNNING && Settings.canDrawOverlays(this@MainActivity)) {
                    FocusOverlayService.prepare(this@MainActivity)
                } else {
                    FocusOverlayService.stop(this@MainActivity)
                }
            }
        }

        // Âm thanh tập trung: phát khi đồng hồ chạy, dừng khi tạm dừng / hoàn thành / đăng xuất.
        lifecycleScope.launch {
            focusRepository.currentSession
                .map { it.state }
                .distinctUntilChanged()
                .collect { state ->
                    if (state == FocusSessionState.RUNNING) {
                        FocusSoundService.startIfEnabled(this@MainActivity)
                    } else {
                        FocusSoundService.stop(this@MainActivity)
                    }
                }
        }

        ClassNotificationHelper.ensureChannel(this)
        UsageNotificationHelper.ensureChannels(this)
        if (com.health.pop.core.tracker.UsageLimitStore(this).trackingEnabled) UsageTrackerWorker.schedule(this)
        ClassNotificationWorker.schedule(this)
        // Khi app đang mở: hỏi quyền thông báo một lần cho học sinh và kiểm tra thông báo mới của giáo viên mỗi 30 giây.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                var askedPermission = false
                while (true) {
                    if (tokenManager.getUser()?.role == "STUDENT") {
                        if (!askedPermission) {
                            askedPermission = true
                            requestNotificationPermissionIfNeeded()
                        }
                        ClassNotificationSync.run(applicationContext, classroomRepository)
                    }
                    delay(30_000)
                }
            }
        }

        setContent {
            POPAITheme {
                val navController = rememberNavController()
                var showSplash by remember { mutableStateOf(true) }
                var updateInfo by remember { mutableStateOf<AppVersionDto?>(null) }
                var showUpdateDialog by remember { mutableStateOf(false) }

                // Kiểm tra phiên bản mới khi ứng dụng mở. Nếu khác phiên bản cài đặt, hiện popup và thông báo.
                LaunchedEffect(Unit) {
                    val result = appUpdateRepository.checkLatestVersion()
                    if (result is ApiResult.Success) {
                        val latest = result.data.latest
                        if (latest != null) {
                            val currentVersion = BuildConfig.VERSION_NAME
                            val currentCode = BuildConfig.VERSION_CODE
                            val serverVersion = latest.version
                            val serverCode = latest.versionCode
                            val versionDifferent = serverVersion != null && serverVersion != currentVersion
                            val codeDifferent = serverCode != null && serverCode != currentCode
                            if (versionDifferent || codeDifferent) {
                                updateInfo = latest
                                showUpdateDialog = true
                                // Thông báo đẩy cục báo (hiện trên thanh thông báo hệ thống).
                                UpdateNotificationHelper.showUpdateAvailable(
                                    this@MainActivity,
                                    latest.version,
                                    latest.apkUrl
                                )
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (showSplash) {
                        PopSplashScreen(onFinished = { showSplash = false })
                    } else {
                        AppNavHost(
                            navController = navController,
                            authRepository = authRepository,
                            assessmentRepository = assessmentRepository,
                            planRepository = planRepository,
                            focusRepository = focusRepository,
                            journalRepository = journalRepository,
                            progressRepository = progressRepository,
                            aiRepository = aiRepository,
                            classroomRepository = classroomRepository,
                            openClassroomRequest = openClassroomRequest,
                            onOpenClassroomHandled = { openClassroomRequest = false },
                            returnToFocusRequest = returnToFocusRequest,
                            onReturnToFocusHandled = { returnToFocusRequest = false },
                            openTrackerRequest = openTrackerRequest,
                            onOpenTrackerHandled = { openTrackerRequest = false }
                        )
                    }

                    if (showUpdateDialog && updateInfo != null) {
                        AlertDialog(
                            onDismissRequest = { showUpdateDialog = false },
                            title = { Text(text = "Có bản cập nhật mới") },
                            text = {
                                Text(
                                    text =
                                        "Phiên bản ${updateInfo?.version ?: "?"} (mã ${updateInfo?.versionCode ?: "?"}) đã sẵn sàng. " +
                                        "Bạn có muốn tải về và cập nhật ngay bây giờ?"
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    showUpdateDialog = false
                                    updateInfo?.apkUrl?.let { url ->
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        startActivity(intent)
                                    }
                                }) {
                                    Text(text = "Cập nhật")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showUpdateDialog = false }) {
                                    Text(text = "Bỏ qua")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_RETURN_TO_FOCUS = "com.health.pop.extra.RETURN_TO_FOCUS"
    }
}

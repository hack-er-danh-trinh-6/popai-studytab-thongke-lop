package com.health.pop.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import com.health.pop.data.local.EncryptedChatHistoryStore
import com.health.pop.data.local.InMemoryChatHistoryStore
import com.health.pop.data.repository.AIRepository
import com.health.pop.data.repository.AssessmentRepository
import com.health.pop.data.repository.AuthRepository
import com.health.pop.data.repository.ClassroomRepository
import com.health.pop.data.repository.FocusRepository
import com.health.pop.data.repository.JournalRepository
import com.health.pop.data.repository.PlanRepository
import com.health.pop.domain.model.AiMode
import com.health.pop.domain.model.AssessmentPhase
import com.health.pop.data.repository.ProgressRepository
import com.health.pop.ui.ai.AIViewModel
import com.health.pop.ui.ai.POPAIChatScreen
import com.health.pop.ui.auth.AuthScreen
import com.health.pop.ui.auth.AuthViewModel
import com.health.pop.ui.classroom.ClassroomScreen
import com.health.pop.ui.classroom.ClassroomViewModel
import com.health.pop.domain.model.FocusSessionState
import com.health.pop.ui.focus.FocusLockOverlay
import com.health.pop.ui.focus.FocusTimerScreen
import com.health.pop.ui.focus.FocusViewModel
import com.health.pop.ui.home.HomeViewModel
import com.health.pop.ui.habits.HabitsScreen
import com.health.pop.ui.habits.HabitsTab
import com.health.pop.ui.home.TodayHomeScreen
import com.health.pop.ui.journal.JournalViewModel
import com.health.pop.ui.onboarding.LocalOnboarding
import com.health.pop.ui.onboarding.OnboardingState
import com.health.pop.ui.onboarding.OnboardingTour
import com.health.pop.ui.plan.PlanViewModel
import com.health.pop.ui.prevention.AssessmentResultScreen
import com.health.pop.ui.prevention.AssessmentScreen
import com.health.pop.ui.prevention.PreventionScreen
import com.health.pop.ui.prevention.PreventionViewModel
import com.health.pop.ui.progress.ProgressScreen
import com.health.pop.ui.progress.ProgressViewModel
import com.health.pop.ui.study.StudyScreen
import com.health.pop.ui.study.StudyViewModel
import com.health.pop.ui.tracker.TrackerScreen
import com.health.pop.ui.tracker.TrackerViewModel

// Nhật ký nằm chung tab Kế hoạch nên hai route này cùng vị trí; Học tập thay chỗ tab Nhật ký cũ.
private val mainTabOrder = mapOf(
    Screen.Home.route to 0,
    Screen.Plan.route to 1,
    Screen.Journal.route to 1,
    Screen.Focus.route to 2,
    Screen.Study.route to 3,
    Screen.Progress.route to 4,
    Screen.AI.route to 5
)

private fun isMainTab(route: String?): Boolean = route in mainTabOrder

/** Các màn dùng khung PopPage (nền xanh tràn sát mép, tự xử lý status bar). */
private val popPageRoutes = setOf(
    Screen.Home.route,
    Screen.Plan.route,
    Screen.Focus.route,
    Screen.Journal.route,
    Screen.Study.route,
    Screen.HomeworkAI.route,
    Screen.Progress.route,
    Screen.AI.route,
    Screen.Prevention.route,
    Screen.Assessment.route,
    Screen.AssessmentResult.route,
    Screen.Classroom.route,
    Screen.Tracker.route
)

// Material "emphasized decelerate": rời nhanh, về đích mượt.
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/** 1 nếu chuyển sang tab bên phải, -1 nếu sang tab bên trái, null nếu không phải chuyển giữa hai tab chính. */
private fun tabDirection(fromRoute: String?, toRoute: String?): Int? {
    val from = mainTabOrder[fromRoute] ?: return null
    val to = mainTabOrder[toRoute] ?: return null
    return if (to >= from) 1 else -1
}

// Fade-through so le: màn cũ mờ đi rất nhanh (90ms) rồi màn mới mới hiện lên (trễ 90ms).
// Nhờ độ trễ này, khung hình đầu tiên (lúc màn mới đang được dựng và thường bị chậm, nhất là bản debug)
// diễn ra khi màn mới còn trong suốt nên không bị cảm giác "giật cục" hay "nhảy cóc" hiệu ứng.
private fun tabEnterTransition(direction: Int): EnterTransition =
    slideInHorizontally(
        animationSpec = tween(360, easing = EmphasizedDecelerate),
        initialOffsetX = { fullWidth -> direction * fullWidth / 6 }
    ) + fadeIn(animationSpec = tween(240, delayMillis = 90, easing = LinearOutSlowInEasing))

private fun tabExitTransition(direction: Int): ExitTransition =
    slideOutHorizontally(
        animationSpec = tween(160, easing = FastOutLinearInEasing),
        targetOffsetX = { fullWidth -> -direction * fullWidth / 12 }
    ) + fadeOut(animationSpec = tween(90, easing = FastOutLinearInEasing))

private fun screenEnterTransition(): EnterTransition =
    fadeIn(animationSpec = tween(240, delayMillis = 90, easing = LinearOutSlowInEasing))

private fun screenExitTransition(): ExitTransition =
    fadeOut(animationSpec = tween(90, easing = FastOutLinearInEasing))

private class PopViewModelFactory<VM : ViewModel>(
    private val createViewModel: () -> VM
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel = createViewModel()
        require(modelClass.isInstance(viewModel))
        return viewModel as T
    }
}

@Composable
private inline fun <reified VM : ViewModel> popViewModel(key: String? = null, noinline create: () -> VM): VM =
    viewModel(key = key, factory = PopViewModelFactory(create))

/**
 * Chuyển sang một tab chính. Dùng chung cho thanh điều hướng dưới và các nút nhanh trên Home để
 * bấm đúp không tạo ra hai màn hình chồng nhau (launchSingleTop) và trạng thái tab được giữ/khôi phục đồng nhất.
 */
private fun NavHostController.navigateToMainTab(route: String) {
    navigate(route) {
        popUpTo(Screen.Home.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Chạy [onResume] mỗi khi [owner] (ví dụ một NavBackStackEntry) quay lại trạng thái RESUMED. */
@Composable
private fun OnResumeEffect(owner: LifecycleOwner, onResume: () -> Unit) {
    val currentOnResume by rememberUpdatedState(onResume)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) currentOnResume()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
}

/** Tìm Activity chứa [Context] (Compose có thể bọc Context nhiều lớp). */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    authRepository: AuthRepository,
    assessmentRepository: AssessmentRepository,
    planRepository: PlanRepository,
    focusRepository: FocusRepository,
    journalRepository: JournalRepository,
    progressRepository: ProgressRepository,
    aiRepository: AIRepository,
    classroomRepository: ClassroomRepository,
    openClassroomRequest: Boolean = false,
    onOpenClassroomHandled: () -> Unit = {},
    returnToFocusRequest: Boolean = false,
    onReturnToFocusHandled: () -> Unit = {},
    openTrackerRequest: Boolean = false,
    onOpenTrackerHandled: () -> Unit = {}
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentUser by authRepository.currentUser.collectAsState()
    // Keep the survey full-height: the fixed tab bar can cover the last row of Likert choices.
    val showBottomBar = currentRoute != Screen.Auth.route && currentRoute != Screen.Assessment.route && currentUser != null
    // currentRoute là null ở khung hình đầu tiên; lúc đó nếu chưa đăng nhập thì điểm đến chính là màn Auth.
    val isAuthFullBleed = currentRoute == Screen.Auth.route || (currentRoute == null && currentUser == null)

    // Trạng thái hướng dẫn riêng cho từng tài khoản: tài khoản mới đăng nhập lần đầu sẽ thấy buổi giới thiệu.
    val context = LocalContext.current
    val onboardingState = remember(currentUser?.id) {
        currentUser?.id?.let { userId ->
            OnboardingState(
                prefs = context.applicationContext.getSharedPreferences("pop_onboarding", Context.MODE_PRIVATE),
                userId = userId
            )
        }
    }

    // Repository là singleton nên sau khi đăng xuất (hoặc phiên hết hạn) phải xoá dữ liệu của tài khoản cũ,
    // nếu không tài khoản đăng nhập tiếp theo sẽ thấy kế hoạch/nhật ký cũ khi mạng lỗi.
    LaunchedEffect(currentUser?.id) {
        if (currentUser == null) {
            planRepository.reset()
            journalRepository.reset()
            focusRepository.reset()
        }
    }

    // Chế độ tập trung: khi đồng hồ đang chạy thì khoá chuyển màn hình/thoát bằng nút Back,
    // và hiện lớp phủ cảnh báo nếu người dùng rời ứng dụng hoặc bị đưa sang màn hình khác.
    val focusSession by focusRepository.currentSession.collectAsState()
    val focusLocked = currentUser != null && focusSession.state == FocusSessionState.RUNNING
    var leftAppWhileFocusing by remember { mutableStateOf(false) }
    val showFocusOverlay = focusLocked &&
        currentRoute != null &&
        (leftAppWhileFocusing || currentRoute != Screen.Focus.route)

    val showFocusLockedToast = {
        Toast.makeText(
            context,
            "Đang trong thời gian tập trung. Hãy tạm dừng hoặc hoàn thành phiên để rời màn hình này.",
            Toast.LENGTH_SHORT
        ).show()
    }

    // Giữ màn hình sáng khi đang tập trung; nếu không, màn hình tự tắt sẽ bị tính là "rời ứng dụng".
    val rootView = LocalView.current
    DisposableEffect(focusLocked) {
        rootView.keepScreenOn = focusLocked
        onDispose { rootView.keepScreenOn = false }
    }

    // Ghi nhận việc rời ứng dụng (Home, đa nhiệm, khoá máy...) khi đang tập trung để hiện cảnh báo lúc quay lại.
    val activityLifecycleOwner = remember(context) { context.findActivity() as? LifecycleOwner }
    val currentFocusLocked by rememberUpdatedState(focusLocked)
    DisposableEffect(activityLifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && currentFocusLocked) leftAppWhileFocusing = true
        }
        activityLifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { activityLifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(focusLocked) {
        if (!focusLocked) leftAppWhileFocusing = false
    }

    // Học sinh vừa đăng ký: hỏi chọn trường và lớp một lần trước khi vào Home.
    var classSetupOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(currentUser, currentRoute) {
        if (currentUser != null && currentRoute == Screen.Auth.route) {
            val askClass = authRepository.consumeClassSetupRequest() && currentUser?.role == "STUDENT"
            classSetupOnboarding = askClass
            navController.navigate(if (askClass) Screen.Classroom.route else Screen.Home.route) {
                popUpTo(Screen.Auth.route) { inclusive = true }
                launchSingleTop = true
            }
        } else if (currentUser == null && currentRoute != null && currentRoute != Screen.Auth.route) {
            navController.navigate(Screen.Auth.route) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    // Người dùng chạm vào thông báo của giáo viên: mở màn Lớp học khi đã đăng nhập.
    LaunchedEffect(openClassroomRequest, currentUser, currentRoute) {
        if (openClassroomRequest && currentUser != null && currentRoute != null && currentRoute != Screen.Auth.route) {
            classSetupOnboarding = false
            navController.navigate(Screen.Classroom.route) { launchSingleTop = true }
            onOpenClassroomHandled()
        }
    }

    LaunchedEffect(returnToFocusRequest, currentUser, currentRoute) {
        if (returnToFocusRequest && currentUser != null && currentRoute != null) {
            classSetupOnboarding = false
            navController.navigateToMainTab(Screen.Focus.route)
            onReturnToFocusHandled()
        }
    }

    // Người dùng chạm vào thông báo "vượt hạn dùng app": mở màn Theo dõi app.
    LaunchedEffect(openTrackerRequest, currentUser, currentRoute) {
        if (openTrackerRequest && currentUser != null && currentRoute != null && currentRoute != Screen.Auth.route) {
            classSetupOnboarding = false
            if (!focusLocked) navController.navigate(Screen.Tracker.route) { launchSingleTop = true }
            onOpenTrackerHandled()
        }
    }

    CompositionLocalProvider(LocalOnboarding provides onboardingState) {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = androidx.compose.material3.MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showBottomBar) {
                        BottomNavBar(
                            currentRoute = currentRoute,
                            locked = focusLocked,
                            onNavigate = { screen ->
                                // Đang tập trung: chỉ cho phép về tab Tập trung; các tab/nút khác chỉ nhắc đang bị khoá.
                                if (!focusLocked || screen.route == Screen.Focus.route) {
                                    navController.navigateToMainTab(screen.route)
                                } else {
                                    showFocusLockedToast()
                                }
                            }
                        )
                    }
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = if (currentUser == null) Screen.Auth.route else Screen.Home.route,
                    // Màn đăng nhập/đăng ký vẽ tràn sát mép (nền xanh dưới status bar) và tự xử lý
                    // status bar / thanh điều hướng / bàn phím, nên không áp padding của Scaffold để tránh cộng đôi.
                    modifier = when {
                        isAuthFullBleed -> Modifier
                        // Mọi màn còn lại đều dùng khung PopPage: tự vẽ nền xanh dưới status bar (tự xử lý status bar),
                        // nên chỉ chừa chỗ cho thanh điều hướng dưới.
                        // consumeWindowInsets: thanh điều hướng dưới đã chiếm chỗ nên imePadding() trong màn hình
                        // (ví dụ ô nhập của POP-AI) chỉ cộng phần bàn phím vượt quá thanh đó, không cộng đôi.
                        currentRoute in popPageRoutes ->
                            Modifier
                                .padding(bottom = innerPadding.calculateBottomPadding())
                                .consumeWindowInsets(PaddingValues(bottom = innerPadding.calculateBottomPadding()))
                        else -> Modifier.padding(innerPadding)
                    },
                    enterTransition = {
                        val direction = tabDirection(initialState.destination.route, targetState.destination.route)
                        if (direction != null) tabEnterTransition(direction) else screenEnterTransition()
                    },
                    exitTransition = {
                        val direction = tabDirection(initialState.destination.route, targetState.destination.route)
                        if (direction != null) tabExitTransition(direction) else screenExitTransition()
                    }
                ) {
                    composable(Screen.Auth.route) {
                        val vm = popViewModel { AuthViewModel(authRepository) }
                        AuthScreen(viewModel = vm, onLoginSuccess = {})
                    }
                    composable(Screen.Home.route) { backStackEntry ->
                        val vm = popViewModel {
                            HomeViewModel(authRepository, planRepository, journalRepository, progressRepository)
                        }
                        // ViewModel của Home được giữ lại khi chuyển tab, nên phải tải lại mỗi khi quay về Home
                        // (sau phiên tập trung, lưu nhật ký/kế hoạch, qua ngày mới, hoặc app quay lại foreground).
                        OnResumeEffect(backStackEntry) { vm.refresh() }
                        TodayHomeScreen(
                            viewModel = vm,
                            onNavigateToFocus = { navController.navigateToMainTab(Screen.Focus.route) },
                            onNavigateToJournal = { navController.navigateToMainTab(Screen.Journal.route) },
                            onNavigateToAssessment = {
                                navController.navigate(Screen.Prevention.route) { launchSingleTop = true }
                            },
                            onNavigateToPlan = { navController.navigateToMainTab(Screen.Plan.route) },
                            onNavigateToClassroom = {
                                classSetupOnboarding = false
                                navController.navigate(Screen.Classroom.route) { launchSingleTop = true }
                            },
                            onNavigateToTracker = {
                                navController.navigate(Screen.Tracker.route) { launchSingleTop = true }
                            },
                            onShowGuide = { onboardingState?.openTour() }
                        )
                    }
                    composable(Screen.Classroom.route) {
                        val vm = popViewModel { ClassroomViewModel(classroomRepository) }
                        ClassroomScreen(
                            viewModel = vm,
                            isOnboarding = classSetupOnboarding,
                            onClose = {
                                classSetupOnboarding = false
                                // Mở từ Home hoặc tab Học tập thì quay về đúng nơi đó; mở đầu tiên sau đăng ký thì về Home.
                                if (navController.previousBackStackEntry != null) {
                                    navController.popBackStack()
                                } else {
                                    navController.navigate(Screen.Home.route) {
                                        popUpTo(Screen.Home.route) { inclusive = true }
                                        launchSingleTop = true
                                    }
                                }
                            }
                        )
                    }
                    composable(Screen.Tracker.route) { backStackEntry ->
                        val vm = popViewModel { TrackerViewModel(context.applicationContext) }
                        // Quay lại từ màn cấp quyền "Truy cập dữ liệu sử dụng" / cài đặt thông báo thì đọc lại trạng thái.
                        OnResumeEffect(backStackEntry) { vm.refresh() }
                        TrackerScreen(viewModel = vm)
                    }
                    composable(Screen.Prevention.route) {
                        val vm = popViewModel { PreventionViewModel(assessmentRepository) }
                        PreventionScreen(viewModel = vm, onStartAssessment = { phase -> navController.navigate("assessment/${phase.code}") })
                    }
                    composable(Screen.Assessment.route, arguments = listOf(navArgument("phase") { type = NavType.StringType })) { entry ->
                        val phase = runCatching { AssessmentPhase.valueOf(entry.arguments?.getString("phase").orEmpty()) }.getOrDefault(AssessmentPhase.PRE)
                        val vm = popViewModel { PreventionViewModel(assessmentRepository, phase) }
                        AssessmentScreen(viewModel = vm, phase = phase, onSubmitted = { navController.navigate(Screen.AssessmentResult.route) })
                    }
                    composable(Screen.AssessmentResult.route) {
                        val vm = popViewModel { PreventionViewModel(assessmentRepository) }
                        AssessmentResultScreen(
                            viewModel = vm,
                            onDone = {
                                navController.navigate(Screen.Home.route) {
                                    popUpTo(Screen.Home.route) { inclusive = true }
                                }
                            }
                        )
                    }
                    composable(Screen.Plan.route) {
                        val planVm = popViewModel { PlanViewModel(planRepository) }
                        val journalVm = popViewModel { JournalViewModel(journalRepository) }
                        HabitsScreen(planViewModel = planVm, journalViewModel = journalVm, initialTab = HabitsTab.PLAN)
                    }
                    // Lối tắt "Ghi nhật ký" từ màn Hôm nay: cùng tab Kế hoạch nhưng mở sẵn mục Nhật ký.
                    composable(Screen.Journal.route) {
                        val planVm = popViewModel { PlanViewModel(planRepository) }
                        val journalVm = popViewModel { JournalViewModel(journalRepository) }
                        HabitsScreen(planViewModel = planVm, journalViewModel = journalVm, initialTab = HabitsTab.JOURNAL)
                    }
                    composable(Screen.Study.route) { backStackEntry ->
                        val userId = currentUser?.id.orEmpty()
                        val isStudent = currentUser?.role == "STUDENT"
                        val vm = popViewModel(key = "study_$userId") { StudyViewModel(classroomRepository, isStudent) }
                        // Quay lại từ màn Lớp học (vào/rời lớp) thì tải lại lớp, thành viên và thời khóa biểu.
                        OnResumeEffect(backStackEntry) { vm.refresh() }
                        StudyScreen(
                            viewModel = vm,
                            isStudent = isStudent,
                            onOpenClassroom = {
                                classSetupOnboarding = false
                                navController.navigate(Screen.Classroom.route) { launchSingleTop = true }
                            },
                            onOpenHomeworkAi = {
                                navController.navigate(Screen.HomeworkAI.route) { launchSingleTop = true }
                            }
                        )
                    }
                    composable(Screen.HomeworkAI.route) {
                        // Cuộc trò chuyện với Giáo sư bài tập lưu riêng, tách khỏi POP-AI trợ lý thói quen.
                        val userId = currentUser?.id.orEmpty()
                        val vm = popViewModel(key = "homework_chat_$userId") {
                            AIViewModel(
                                repository = aiRepository,
                                historyStore = if (userId.isBlank()) {
                                    InMemoryChatHistoryStore()
                                } else {
                                    EncryptedChatHistoryStore(context.applicationContext, "${userId}_homework")
                                },
                                mode = AiMode.HOMEWORK
                            )
                        }
                        POPAIChatScreen(
                            viewModel = vm,
                            mode = AiMode.HOMEWORK,
                            onBack = { navController.popBackStack() }
                        )
                    }
                    composable(Screen.Focus.route) {
                        val plan = planRepository.currentPlan.value
                        val vm = popViewModel {
                            FocusViewModel(
                                repository = focusRepository,
                                defaultTargetMinutes = plan.sessionDurationMinutes,
                                defaultTaskName = plan.studyTasks.firstOrNull().orEmpty()
                            )
                        }
                        FocusTimerScreen(viewModel = vm)
                    }
                    composable(Screen.Progress.route) {
                        val vm = popViewModel { ProgressViewModel(progressRepository) }
                        ProgressScreen(viewModel = vm)
                    }
                    composable(Screen.AI.route) {
                        // Lịch sử chat lưu riêng từng tài khoản (mã hoá trong bộ nhớ app); key theo tài khoản để
                        // đổi tài khoản không dùng lại ViewModel (và cuộc trò chuyện) của người trước.
                        val userId = currentUser?.id.orEmpty()
                        val vm = popViewModel(key = "ai_chat_$userId") {
                            AIViewModel(
                                repository = aiRepository,
                                historyStore = if (userId.isBlank()) {
                                    InMemoryChatHistoryStore()
                                } else {
                                    EncryptedChatHistoryStore(context.applicationContext, userId)
                                }
                            )
                        }
                        POPAIChatScreen(viewModel = vm)
                    }
                }
            }

            if (onboardingState != null) {
                AnimatedVisibility(
                    visible = onboardingState.isTourVisible && currentRoute != Screen.Auth.route,
                    enter = fadeIn(animationSpec = tween(220)),
                    exit = fadeOut(animationSpec = tween(220))
                ) {
                    OnboardingTour(onFinish = onboardingState::finishTour)
                }
            }

            // Đăng ký SAU NavHost để được ưu tiên hơn handler Back của NavHost: chặn Back (kể cả Back để
            // thoát ứng dụng) trong lúc đồng hồ chạy.
            BackHandler(enabled = focusLocked) { showFocusLockedToast() }

            // Đặt cuối cùng để nằm trên mọi màn hình và chặn mọi thao tác bên dưới.
            FocusLockOverlay(
                visible = showFocusOverlay,
                leftApp = leftAppWhileFocusing,
                taskName = focusSession.taskName,
                targetMinutes = focusSession.targetMinutes,
                onReturn = {
                    leftAppWhileFocusing = false
                    navController.navigateToMainTab(Screen.Focus.route)
                }
            )
        }
    }
}

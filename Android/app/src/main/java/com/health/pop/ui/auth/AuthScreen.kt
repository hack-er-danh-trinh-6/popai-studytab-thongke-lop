package com.health.pop.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Cầu nối giữa giao diện mới [PopAuthScreen] và [AuthViewModel].
 * Chữ ký giữ nguyên nên chỗ gọi trong AppNavHost không đổi (AppNavHost chỉ bỏ padding của Scaffold cho màn này).
 */
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onLoginSuccess: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    // Phần đầu màn hình luôn là nền xanh nên icon status bar cần màu sáng (cả chế độ sáng lẫn tối).
    LightStatusBarIconsOnHero()

    // Chạy một lần khi đăng nhập thành công, thay vì gọi trực tiếp trong lúc compose.
    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) onLoginSuccess()
    }

    PopAuthScreen(
        isLoading = uiState is AuthUiState.Loading,
        errorMessage = (uiState as? AuthUiState.Error)?.message,
        onLogin = { identifier, password ->
            viewModel.emailState.value = identifier
            viewModel.passwordState.value = password
            viewModel.login()
        },
        onRegister = { name, identifier, password ->
            viewModel.fullNameState.value = name
            viewModel.emailState.value = identifier
            viewModel.passwordState.value = password
            viewModel.register()
        },
        onClearError = viewModel::clearError,
    )
}

@Composable
private fun LightStatusBarIconsOnHero() {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view) {
        val window = view.context.findActivity()?.window
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

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

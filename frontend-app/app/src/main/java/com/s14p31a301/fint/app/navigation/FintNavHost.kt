package com.s14p31a301.fint.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.s14p31a301.fint.core.network.interceptor.UnauthorizedInterceptor
import com.s14p31a301.fint.core.webview.WebViewRoute
import com.s14p31a301.fint.feature.businesscard.presentation.BusinessCardScanScreen
import com.s14p31a301.fint.feature.devicecontact.presentation.DeviceContactListScreen
import com.s14p31a301.fint.feature.recording.presentation.MeetingRecordingScreen
import com.s14p31a301.fint.feature.web.WebScreen
import com.s14p31a301.fint.feature.web.WebViewModel
import org.koin.compose.koinInject

/**
 * 앱 NavHost.
 * 기본 시작점은 WebView(Web), Bridge 호출에 의해 Native 화면으로 이동.
 *
 * - Native 화면 완료 → WebView reload / loadUrl 트리거
 * - 401 응답 (UnauthorizedInterceptor) → 토큰 클리어 + 로그인 페이지로 이동
 */
@Composable
fun FintNavHost() {
    val navController = rememberNavController()
    val unauthorizedInterceptor: UnauthorizedInterceptor = koinInject()

    // WebScreen이 노출하는 ViewModel 참조
    val webViewModelRef = remember { mutableStateOf<WebViewModel?>(null) }

    // 401 이벤트 처리: 토큰 클리어 + 로그인 페이지 이동
    LaunchedEffect(unauthorizedInterceptor) {
        unauthorizedInterceptor.events.collect {
            webViewModelRef.value?.clearAuth()
            webViewModelRef.value?.loadUrl(WebViewRoute.login())
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.Web.path,
    ) {
        composable(AppRoute.Web.path) {
            WebScreen(
                onOpenBusinessCardScanner = {
                    navController.navigate(AppRoute.BusinessCardScan.path)
                },
                onOpenDeviceContactPicker = {
                    navController.navigate(AppRoute.DeviceContactList.path)
                },
                onOpenMeetingRecorder = { activityId ->
                    val route = AppRoute.MeetingRecording.path +
                        "?activityId=${activityId ?: ""}"
                    navController.navigate(route)
                },
                onWebViewReady = { vm -> webViewModelRef.value = vm },
            )
        }

        composable(AppRoute.BusinessCardScan.path) {
            BusinessCardScanScreen(
                onCaptured = {
                    // 등록 완료 후 담당자 목록 reload
                    webViewModelRef.value?.loadUrl(WebViewRoute.contacts())
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(AppRoute.DeviceContactList.path) {
            DeviceContactListScreen(
                onSelect = { _ ->
                    webViewModelRef.value?.loadUrl(WebViewRoute.contacts())
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = AppRoute.MeetingRecording.path + "?activityId={activityId}",
            arguments = listOf(
                navArgument("activityId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { backStackEntry ->
            val activityId = backStackEntry.arguments
                ?.getString("activityId")
                ?.toLongOrNull()
            MeetingRecordingScreen(
                activityId = activityId,
                onFinished = { _ ->
                    activityId?.let {
                        webViewModelRef.value?.loadUrl(WebViewRoute.activity(it))
                    } ?: webViewModelRef.value?.reload()
                    navController.popBackStack()
                },
                onCancel = { navController.popBackStack() },
            )
        }
    }
}

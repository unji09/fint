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
import androidx.navigation.navigation
import com.s14p31a301.fint.core.network.interceptor.UnauthorizedInterceptor
import com.s14p31a301.fint.core.webview.WebViewRoute
import com.s14p31a301.fint.feature.businesscard.presentation.BusinessCardResultScreen
import com.s14p31a301.fint.feature.businesscard.presentation.BusinessCardScanScreen
import com.s14p31a301.fint.feature.devicecontact.presentation.DeviceContactListScreen
import com.s14p31a301.fint.feature.devicecontact.presentation.DeviceContactSelectScreen
import com.s14p31a301.fint.feature.devicecontact.presentation.DeviceContactViewModel
import com.s14p31a301.fint.feature.recording.presentation.MeetingRecordingScreen
import com.s14p31a301.fint.feature.web.WebScreen
import com.s14p31a301.fint.feature.web.WebViewModel
import org.koin.androidx.compose.koinViewModel
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
                    navController.navigate(AppRoute.DeviceContactGraph.path)
                },
                onOpenMeetingRecorder = { activityId ->
                    val route = AppRoute.MeetingRecording.path +
                        "?activityId=${activityId ?: ""}"
                    navController.navigate(route)
                },
                onWebViewReady = { vm -> webViewModelRef.value = vm },
            )
        }

        // ----- 명함 OCR -----
        composable(AppRoute.BusinessCardScan.path) {
            BusinessCardScanScreen(
                onCaptured = { imagePath ->
                    navController.navigate(AppRoute.BusinessCardResult.build(imagePath))
                },
                onCancel = { navController.popBackStack() },
            )
        }

        composable(
            route = AppRoute.BusinessCardResult.path,
            arguments = listOf(
                navArgument("imagePath") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            ),
        ) { backStackEntry ->
            val imagePath = backStackEntry.arguments?.getString("imagePath")
            BusinessCardResultScreen(
                imagePath = imagePath,
                onRegistered = {
                    webViewModelRef.value?.loadUrl(WebViewRoute.contacts())
                    navController.popBackStack(AppRoute.Web.path, inclusive = false)
                },
                onRetake = {
                    navController.popBackStack(AppRoute.BusinessCardScan.path, inclusive = false)
                },
                onCancel = {
                    navController.popBackStack(AppRoute.Web.path, inclusive = false)
                },
            )
        }

        // ----- 기기 연락처 graph (list + select 가 같은 VM 공유) -----
        navigation(
            startDestination = AppRoute.DeviceContactList.path,
            route = AppRoute.DeviceContactGraph.path,
        ) {
            composable(AppRoute.DeviceContactList.path) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(AppRoute.DeviceContactGraph.path)
                }
                val vm: DeviceContactViewModel = koinViewModel(viewModelStoreOwner = parentEntry)
                DeviceContactListScreen(
                    viewModel = vm,
                    onSelect = { id ->
                        navController.navigate(AppRoute.DeviceContactSelect.build(id))
                    },
                    onCancel = { navController.popBackStack(AppRoute.Web.path, false) },
                )
            }

            composable(
                route = AppRoute.DeviceContactSelect.path,
                arguments = listOf(navArgument("contactId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(AppRoute.DeviceContactGraph.path)
                }
                val vm: DeviceContactViewModel = koinViewModel(viewModelStoreOwner = parentEntry)
                val contactId = backStackEntry.arguments?.getString("contactId").orEmpty()
                DeviceContactSelectScreen(
                    viewModel = vm,
                    contactId = contactId,
                    onRegistered = {
                        webViewModelRef.value?.loadUrl(WebViewRoute.contacts())
                        navController.popBackStack(AppRoute.Web.path, false)
                    },
                    onCancel = { navController.popBackStack() },
                )
            }
        }

        // ----- 미팅 녹음 (Phase 6) -----
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

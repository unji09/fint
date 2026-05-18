package com.s14p31a301.fint.feature.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.s14p31a301.fint.core.webview.FintWebView
import com.s14p31a301.fint.core.webview.WebViewBridge
import com.s14p31a301.fint.core.webview.WebViewRoute
import com.s14p31a301.fint.feature.debug.DebugEntryOverlay
import org.koin.androidx.compose.koinViewModel

/**
 * 앱 진입 후 보이는 메인 WebView 화면.
 * - 로그인 / 대시보드 / 고객사 / 담당자 / 영업건 / 활동 / 설정 모두 여기서 표시.
 * - Bridge 호출 시 Native 화면으로 navigate.
 *
 * @param onWebViewReady NavHost가 같은 ViewModel 인스턴스를 잡아 Native 완료 시 reload/loadUrl 호출용.
 */
@Composable
fun WebScreen(
    onOpenBusinessCardScanner: () -> Unit,
    onOpenDeviceContactPicker: () -> Unit,
    onOpenMeetingRecorder: (activityId: Long?) -> Unit,
    onWebViewReady: (WebViewModel) -> Unit = {},
) {
    val webViewModel: WebViewModel = koinViewModel()

    // ViewModel을 NavHost 측에 노출 (한 번만)
    remember(webViewModel) {
        onWebViewReady(webViewModel)
        webViewModel
    }

    val isLoggedIn by webViewModel.isLoggedIn.collectAsState()

    val bridge = remember(webViewModel) {
        WebViewBridge(
            onSaveAuthToken = webViewModel::saveAuthToken,
            onLoggedOut = webViewModel::clearAuth,
            onOpenBusinessCardScanner = onOpenBusinessCardScanner,
            onOpenDeviceContactPicker = onOpenDeviceContactPicker,
            onOpenMeetingRecorder = onOpenMeetingRecorder,
        )
    }

    Box(Modifier.fillMaxSize()) {
        FintWebView(
            url = WebViewRoute.dashboard(),
            bridge = bridge,
            commands = webViewModel.commands,
            onPageStarted = webViewModel::onPageStarted,
            onPageFinished = webViewModel::onPageFinished,
        )

        if (isLoggedIn) {
            DebugEntryOverlay(
                onOpenBusinessCardScanner = onOpenBusinessCardScanner,
                onOpenDeviceContactPicker = onOpenDeviceContactPicker,
            )
        }
    }
}

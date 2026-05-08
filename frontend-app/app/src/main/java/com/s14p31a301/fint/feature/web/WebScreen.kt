package com.s14p31a301.fint.feature.web

import androidx.compose.runtime.Composable

/**
 * 앱 진입 후 보이는 메인 WebView 화면.
 * - 로그인 / 대시보드 / 고객사 / 담당자 / 영업건 / 활동 / 설정 모두 여기서 표시.
 * - 브리지 호출 시 Native 화면으로 navigate.
 */
@Composable
fun WebScreen(
    onOpenBusinessCardScanner: () -> Unit,
    onOpenDeviceContactPicker: () -> Unit,
    onOpenMeetingRecorder: (activityId: Long?) -> Unit,
) {
    // TODO: FintWebView(url = WebViewRoute.dashboard(), bridge = ...)
}


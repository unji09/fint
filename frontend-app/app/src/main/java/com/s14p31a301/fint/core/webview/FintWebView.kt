package com.s14p31a301.fint.core.webview

import androidx.compose.runtime.Composable

/**
 * 앱의 메인 WebView Composable.
 * - WebViewConfig 적용 (JS, DomStorage, FileChooser 등)
 * - WebViewBridge addJavascriptInterface
 * - 외부 URL/딥링크 처리
 */
@Composable
fun FintWebView(
    url: String,
    bridge: WebViewBridge,
) {
    // TODO: AndroidView { WebView(...) }
}


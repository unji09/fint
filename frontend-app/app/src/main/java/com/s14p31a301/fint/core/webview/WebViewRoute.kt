package com.s14p31a301.fint.core.webview

/**
 * WebView 내부에서 사용할 URL 경로 매핑.
 * 푸시 알림 클릭 시 어디로 이동할지 등을 결정한다.
 */
object WebViewRoute {
    fun dashboard() = "${WebViewConfig.WEB_BASE_URL}/"
    fun login() = "${WebViewConfig.WEB_BASE_URL}/login"
    fun contacts() = "${WebViewConfig.WEB_BASE_URL}/contacts"
    fun activity(activityId: Long) = "${WebViewConfig.WEB_BASE_URL}/activities/$activityId"
    fun signal(signalId: Long) = "${WebViewConfig.WEB_BASE_URL}/signals/$signalId"
}

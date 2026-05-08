package com.s14p31a301.fint.core.notification

import com.s14p31a301.fint.core.webview.WebViewRoute

/**
 * 알림 type → WebView URL 매핑.
 * 알림 클릭 시 대부분 WebView 의 특정 URL 로 이동한다.
 */
object NotificationRouter {
    fun resolveUrl(type: String, payload: Map<String, String>): String? = when (type) {
        "ACTIVITY_REMINDER" -> payload["activityId"]?.toLongOrNull()?.let(WebViewRoute::activity)
        "SIGNAL_ALERT" -> payload["signalId"]?.toLongOrNull()?.let(WebViewRoute::signal)
        "STT_DONE" -> payload["activityId"]?.toLongOrNull()?.let(WebViewRoute::activity)
        else -> null
    }
}


package com.s14p31a301.fint.core.webview

import com.s14p31a301.fint.BuildConfig

/**
 * WebView 공통 설정.
 * - JavaScript / DomStorage / Cookie / UserAgent / Cache 정책
 */
object WebViewConfig {
    val WEB_BASE_URL: String = BuildConfig.WEB_BASE_URL
    const val USER_AGENT_SUFFIX = "FintAndroid/1.0"
}


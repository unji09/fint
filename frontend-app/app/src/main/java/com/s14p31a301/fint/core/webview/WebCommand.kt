package com.s14p31a301.fint.core.webview

/**
 * Native → WebView 로 보낼 명령(예: reload, navigate, postMessage).
 */
sealed interface WebCommand {
    data class LoadUrl(val url: String) : WebCommand
    data object Reload : WebCommand
    data class EvaluateJs(val script: String) : WebCommand
}


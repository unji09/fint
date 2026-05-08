package com.s14p31a301.fint.core.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * 앱의 메인 WebView Composable.
 * - WebViewConfig 적용 (JS, DomStorage, Cookie, FileChooser 등)
 * - WebViewBridge addJavascriptInterface
 * - 외부 URL/딥링크 처리
 * - WebCommand 처리 (Native → WebView: Reload / LoadUrl / EvaluateJs)
 * - 뒤로가기: WebView history 우선
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FintWebView(
    url: String,
    bridge: WebViewBridge,
    modifier: Modifier = Modifier,
    commands: Flow<WebCommand> = emptyFlow(),
    onPageStarted: (String) -> Unit = {},
    onPageFinished: (String) -> Unit = {},
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // 파일 업로드 콜백 보관 (WebChromeClient.onShowFileChooser 가 넘겨주는 콜백)
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uris: Array<Uri>? = WebChromeClient.FileChooserParams
            .parseResult(result.resultCode, result.data)
        fileChooserCallback?.onReceiveValue(uris ?: emptyArray())
        fileChooserCallback = null
    }

    BackHandler(enabled = webViewRef?.canGoBack() == true) {
        webViewRef?.goBack()
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                // 쿠키 정책: WebView 로그인 세션 유지를 위해 1st/3rd party 모두 허용
                CookieManager.getInstance().setAcceptCookie(true)

                WebView(ctx).apply {
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                        userAgentString = "$userAgentString ${WebViewConfig.USER_AGENT_SUFFIX}"
                        allowContentAccess = true
                        allowFileAccess = false
                        // input[type=file] capture=camera 등을 위해 미디어 자동재생 허용
                        mediaPlaybackRequiresUserGesture = false
                    }

                    addJavascriptInterface(bridge, WebViewBridge.NAME)

                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, pageUrl: String?, favicon: Bitmap?) {
                            isLoading = true
                            pageUrl?.let(onPageStarted)
                        }

                        override fun onPageFinished(view: WebView?, pageUrl: String?) {
                            isLoading = false
                            pageUrl?.let(onPageFinished)
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ): Boolean = handleUrlOverride(view, request.url)
                    }

                    webChromeClient = object : WebChromeClient() {
                        override fun onShowFileChooser(
                            webView: WebView?,
                            filePathCallback: ValueCallback<Array<Uri>>?,
                            fileChooserParams: FileChooserParams?,
                        ): Boolean {
                            // 이전 콜백이 남아있으면 cancel
                            fileChooserCallback?.onReceiveValue(null)
                            fileChooserCallback = filePathCallback

                            return runCatching {
                                val intent = fileChooserParams?.createIntent()
                                if (intent != null) {
                                    fileChooserLauncher.launch(intent)
                                    true
                                } else {
                                    fileChooserCallback = null
                                    false
                                }
                            }.getOrElse {
                                fileChooserCallback = null
                                false
                            }
                        }
                    }

                    loadUrl(url)
                    webViewRef = this
                }
            },
        )

        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
            )
        }
    }

    // Native → WebView 명령 처리
    LaunchedEffect(webViewRef, commands) {
        val web = webViewRef ?: return@LaunchedEffect
        commands.collect { cmd ->
            when (cmd) {
                is WebCommand.Reload -> web.reload()
                is WebCommand.LoadUrl -> web.loadUrl(cmd.url)
                is WebCommand.EvaluateJs -> web.evaluateJavascript(cmd.script, null)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            webViewRef?.apply {
                stopLoading()
                removeJavascriptInterface(WebViewBridge.NAME)
                destroy()
            }
            webViewRef = null
        }
    }
}

/**
 * 외부 스킴(tel, mailto, intent 등) 또는 도메인 외부 URL은 시스템 브라우저/앱으로 위임.
 * 자체 도메인은 WebView 내부에서 로드.
 */
private fun handleUrlOverride(view: WebView, target: Uri): Boolean {
    val scheme = target.scheme ?: return false
    return when (scheme) {
        "http", "https" -> {
            val host = target.host ?: return false
            val baseHost = WebViewConfig.WEB_BASE_URL.toUri().host
            if (baseHost != null && host.endsWith(baseHost)) {
                false // 내부 URL → WebView 로 처리
            } else {
                runCatching {
                    view.context.startActivity(
                        Intent(Intent.ACTION_VIEW, target).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                }
                true
            }
        }
        else -> {
            // tel, mailto, intent 등
            runCatching {
                view.context.startActivity(
                    Intent(Intent.ACTION_VIEW, target).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                )
            }
            true
        }
    }
}

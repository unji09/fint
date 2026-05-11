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
                            // 폴백: 웹이 명시적으로 window.Android.saveAuthToken 을 호출하지 않더라도
                            // localStorage / sessionStorage / cookie 에 저장된 토큰을 자동 추출하여
                            // 네이티브 DataStore 로 동기화한다.
                            view?.evaluateJavascript(TOKEN_SYNC_JS, null)
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

/**
 * 페이지 로드 후 한 번 실행되어 웹의 토큰 저장소를 스캔, 발견 시
 * `window.Android.saveAuthToken(access, refresh)` 로 네이티브에 전달.
 *
 * 검색 우선순위:
 *  1) localStorage / sessionStorage 의 흔한 키들
 *  2) cookie 의 access_token / accessToken
 *
 * 실패해도 무시 (try/catch).
 */
private val TOKEN_SYNC_JS: String = """
(function () {
  function dlog(m){ try{ if(window.Android && window.Android.debugLog) window.Android.debugLog(String(m)); }catch(e){} console.log('[FintBridge]', m); }
  try {
    if (!window.Android || typeof window.Android.saveAuthToken !== 'function') {
      dlog('Android bridge missing'); return;
    }

    var ACCESS_KEYS = ['accessToken','access_token','ACCESS_TOKEN','authToken','jwt','token','idToken','id_token'];
    var REFRESH_KEYS = ['refreshToken','refresh_token','REFRESH_TOKEN'];

    function isJwt(v){
      return typeof v === 'string' && v.split('.').length === 3 && v.length >= 40;
    }
    function stripQuotes(v){
      if (typeof v !== 'string') return v;
      if (v.length >= 2 && v[0] === '"' && v[v.length-1] === '"') {
        try { v = JSON.parse(v); } catch(e){}
      }
      // "Bearer xxx" prefix 제거
      if (typeof v === 'string' && /^Bearer\s+/i.test(v)) v = v.replace(/^Bearer\s+/i, '');
      return v;
    }
    function probe(store, label){
      if (!store) return [null,null];
      var foundAccess = null, foundRefresh = null;
      // 1) 직접 키
      for (var i = 0; i < ACCESS_KEYS.length; i++) {
        var v = stripQuotes(store.getItem(ACCESS_KEYS[i]));
        if (v) {
          dlog(label + '.' + ACCESS_KEYS[i] + ' len=' + v.length + ' jwt=' + isJwt(v) + ' head=' + v.slice(0,16));
          if (!foundAccess || (isJwt(v) && !isJwt(foundAccess))) foundAccess = v;
        }
      }
      for (var r = 0; r < REFRESH_KEYS.length; r++) {
        var rv = stripQuotes(store.getItem(REFRESH_KEYS[r]));
        if (rv) { foundRefresh = rv; dlog(label + '.' + REFRESH_KEYS[r] + ' len=' + rv.length); }
      }
      // 2) JSON 묶음 스캔
      for (var k = 0; k < store.length; k++) {
        var key = store.key(k);
        var raw = store.getItem(key);
        if (!raw || (raw[0] !== '{' && raw[0] !== '[')) continue;
        try {
          var obj = JSON.parse(raw);
          var stack = [obj];
          while (stack.length) {
            var cur = stack.pop();
            if (!cur || typeof cur !== 'object') continue;
            for (var ak in cur) {
              if (!Object.prototype.hasOwnProperty.call(cur, ak)) continue;
              var val = cur[ak];
              if (typeof val === 'object' && val !== null) { stack.push(val); continue; }
              if (typeof val !== 'string') continue;
              if (ACCESS_KEYS.indexOf(ak) >= 0) {
                dlog(label + '[' + key + '].' + ak + ' len=' + val.length + ' jwt=' + isJwt(val));
                if (!foundAccess || (isJwt(val) && !isJwt(foundAccess))) foundAccess = val;
              }
              if (REFRESH_KEYS.indexOf(ak) >= 0) {
                if (!foundRefresh) foundRefresh = val;
              }
            }
          }
        } catch (e) {}
      }
      return [foundAccess, foundRefresh];
    }

    function readCookie(name){
      var m = document.cookie.match(new RegExp('(?:^|; )' + name + '=([^;]*)'));
      return m ? decodeURIComponent(m[1]) : null;
    }

    var pa = probe(window.localStorage, 'local');
    var sa = probe(window.sessionStorage, 'session');

    var access = (isJwt(pa[0]) ? pa[0] : null)
              || (isJwt(sa[0]) ? sa[0] : null)
              || pa[0] || sa[0]
              || readCookie('accessToken') || readCookie('access_token');
    var refresh = pa[1] || sa[1]
               || readCookie('refreshToken') || readCookie('refresh_token') || '';

    if (access && access.length > 10) {
      dlog('push saveAuthToken access.len=' + access.length + ' jwt=' + isJwt(access) + ' refresh.len=' + (refresh ? refresh.length : 0));
      window.Android.saveAuthToken(access, refresh || '');
    } else {
      dlog('no token found in storages. cookieKeys=' + document.cookie.split(';').map(function(c){return c.trim().split('=')[0];}).join(','));
    }
  } catch (e) {
    dlog('token sync failed: ' + (e && e.message));
  }
})();
""".trimIndent()


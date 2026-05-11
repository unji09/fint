package com.s14p31a301.fint.core.network.interceptor

import android.util.Log
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 인증 만료(401/403) 감지 → 전역 이벤트 발행.
 *
 * 수신측(Phase 3 wiring):
 *   - WebView 로그인 페이지로 이동 (`WebViewModel.loadUrl(login())`)
 *   - 또는 refresh token 으로 갱신 시도 후 재시도
 *
 * MVP 에서는 단순히 토큰 클리어 + 로그인 페이지로 이동을 권장.
 *
 * NOTE: 백엔드가 토큰 없음/만료 시 401 대신 **403** 으로 응답할 수 있어
 * 진단 가능하도록 본문도 함께 로그한다.
 */
class UnauthorizedInterceptor : Interceptor {

    private val _events = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.code == 401 || response.code == 403) {
            // peekBody 는 본문을 소비하지 않고 별도로 읽기 때문에 downstream 에 영향 없음
            val errBody = runCatching {
                response.peekBody(2048).string()
            }.getOrNull()
            val hasAuth = request.header("Authorization") != null
            Log.w(
                TAG,
                "AUTH ${response.code} ${request.method} ${request.url}  hasAuth=$hasAuth  body=$errBody"
            )
            if (response.code == 401) {
                _events.tryEmit(Unit)
            }
        }
        return response
    }

    companion object {
        private const val TAG = "AuthFail"
    }
}


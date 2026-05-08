package com.s14p31a301.fint.core.network.interceptor

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 인증 만료(401) 감지 → 전역 이벤트 발행.
 *
 * 수신측(Phase 3 wiring):
 *   - WebView 로그인 페이지로 이동 (`WebViewModel.loadUrl(login())`)
 *   - 또는 refresh token 으로 갱신 시도 후 재시도
 *
 * MVP 에서는 단순히 토큰 클리어 + 로그인 페이지로 이동을 권장.
 */
class UnauthorizedInterceptor : Interceptor {

    private val _events = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<Unit> = _events.asSharedFlow()

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (response.code == 401) {
            _events.tryEmit(Unit)
        }
        return response
    }
}


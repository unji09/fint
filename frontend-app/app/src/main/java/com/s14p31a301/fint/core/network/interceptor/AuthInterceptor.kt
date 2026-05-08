package com.s14p31a301.fint.core.network.interceptor

/**
 * Authorization 헤더(Bearer) 자동 부여 인터셉터.
 * TokenDataStore에서 accessToken을 읽어와 사용한다.
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?,
) {
    // TODO: implements okhttp3.Interceptor
}


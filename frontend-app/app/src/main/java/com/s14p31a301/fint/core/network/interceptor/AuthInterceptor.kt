package com.s14p31a301.fint.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Authorization 헤더(Bearer) 자동 부여 인터셉터.
 * TokenDataStore에서 accessToken을 읽어와 사용한다.
 *
 * - 헤더에 이미 Authorization 이 붙어 있으면 건드리지 않음
 * - 토큰 없으면 그대로 진행 (로그인 화면 등)
 */
class AuthInterceptor(
    private val tokenProvider: () -> String?,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        if (original.header(HEADER_AUTHORIZATION) != null) {
            return chain.proceed(original)
        }

        val token = tokenProvider()
        val request = if (token.isNullOrBlank()) {
            original
        } else {
            original.newBuilder()
                .header(HEADER_AUTHORIZATION, "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }

    companion object {
        const val HEADER_AUTHORIZATION = "Authorization"
    }
}

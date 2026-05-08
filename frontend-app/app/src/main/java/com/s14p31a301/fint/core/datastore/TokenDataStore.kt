package com.s14p31a301.fint.core.datastore

/**
 * accessToken / refreshToken 저장소.
 * 웹 로그인 성공 시 WebViewBridge.saveAuthToken()으로 전달받아 보관한다.
 */
interface TokenDataStore {
    suspend fun saveTokens(accessToken: String, refreshToken: String)
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?
    suspend fun clear()
}


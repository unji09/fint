package com.s14p31a301.fint.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * DataStore Preferences 기반 TokenDataStore 구현.
 *
 * NOTE(Phase 8 보안):
 * - 현재는 평문 Preferences. 운영 단계에서 EncryptedSharedPreferences /
 *   Jetpack Security Crypto 기반 암호화 저장으로 교체할 것.
 */
private val Context.tokenDataStorePrefs by preferencesDataStore(name = "fint_token_store")

class TokenDataStoreImpl(
    private val appContext: Context,
) : TokenDataStore {

    private val accessKey = stringPreferencesKey("access_token")
    private val refreshKey = stringPreferencesKey("refresh_token")

    override suspend fun saveTokens(accessToken: String, refreshToken: String) {
        appContext.tokenDataStorePrefs.edit { prefs ->
            prefs[accessKey] = accessToken
            prefs[refreshKey] = refreshToken
        }
    }

    override suspend fun getAccessToken(): String? =
        appContext.tokenDataStorePrefs.data
            .map { it[accessKey] }
            .first()

    override suspend fun getRefreshToken(): String? =
        appContext.tokenDataStorePrefs.data
            .map { it[refreshKey] }
            .first()

    override suspend fun clear() {
        appContext.tokenDataStorePrefs.edit { it.clear() }
    }
}


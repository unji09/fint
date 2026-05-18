package com.s14p31a301.fint.feature.web

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.s14p31a301.fint.core.datastore.TokenDataStore
import com.s14p31a301.fint.core.webview.WebCommand
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * WebView 화면 상태 + Native → WebView 명령 채널 + 인증 토큰 저장.
 * Koin viewModel { ... } 으로 등록 (Phase 3).
 */
class WebViewModel(
    private val tokenDataStore: TokenDataStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WebUiState())
    val uiState: StateFlow<WebUiState> = _uiState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    init {
        viewModelScope.launch {
            _isLoggedIn.value = !tokenDataStore.getAccessToken().isNullOrEmpty()
        }
    }

    private val _commands = MutableSharedFlow<WebCommand>(
        replay = 0,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val commands: SharedFlow<WebCommand> = _commands.asSharedFlow()

    fun onPageStarted(url: String) {
        _uiState.value = _uiState.value.copy(currentUrl = url, isLoading = true, error = null)
    }

    fun onPageFinished(url: String) {
        _uiState.value = _uiState.value.copy(currentUrl = url, isLoading = false)
    }

    fun onPageError(message: String) {
        _uiState.value = _uiState.value.copy(isLoading = false, error = message)
    }

    /** Native 화면에서 작업 완료 후 WebView 갱신을 트리거 */
    fun reload() {
        _commands.tryEmit(WebCommand.Reload)
    }

    fun loadUrl(url: String) {
        _commands.tryEmit(WebCommand.LoadUrl(url))
    }

    fun evaluateJs(script: String) {
        _commands.tryEmit(WebCommand.EvaluateJs(script))
    }

    /** 웹 로그인 성공 → Bridge.saveAuthToken → 여기로 위임 */
    fun saveAuthToken(accessToken: String, refreshToken: String) {
        android.util.Log.d(
            "WebVM",
            "saveAuthToken received (accessLen=${accessToken.length}, refreshLen=${refreshToken.length})"
        )
        viewModelScope.launch {
            tokenDataStore.saveTokens(accessToken, refreshToken)
            _isLoggedIn.value = true
            android.util.Log.d("WebVM", "saveAuthToken persisted to DataStore")
        }
    }

    fun clearAuth() {
        _isLoggedIn.value = false
        viewModelScope.launch { tokenDataStore.clear() }
    }
}

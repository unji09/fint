package com.s14p31a301.fint.core.webview

import android.util.Log
import android.webkit.JavascriptInterface

/**
 * 웹 → Native 통신 브리지.
 * 웹에서 window.Android.* 로 호출.
 */
class WebViewBridge(
    private val onSaveAuthToken: (accessToken: String, refreshToken: String) -> Unit,
    private val onLoggedOut: () -> Unit,
    private val onOpenBusinessCardScanner: () -> Unit,
    private val onOpenDeviceContactPicker: () -> Unit,
    private val onOpenMeetingRecorder: (activityId: Long?) -> Unit,
) {
    @JavascriptInterface
    fun saveAuthToken(accessToken: String, refreshToken: String) {
        onSaveAuthToken(accessToken, refreshToken)
    }

    @JavascriptInterface
    fun notifyLoggedOut() {
        onLoggedOut()
    }

    /** 디버그 로그 (웹 → 네이티브 Logcat 으로 흘려보내기) */
    @JavascriptInterface
    fun debugLog(msg: String) {
        Log.d("WebJS", msg)
    }

    @JavascriptInterface
    fun openBusinessCardScanner() {
        onOpenBusinessCardScanner()
    }

    @JavascriptInterface
    fun openDeviceContactPicker() {
        onOpenDeviceContactPicker()
    }

    @JavascriptInterface
    fun openMeetingRecorder(activityId: String?) {
        onOpenMeetingRecorder(activityId?.toLongOrNull())
    }

    companion object {
        const val NAME = "Android"
    }
}


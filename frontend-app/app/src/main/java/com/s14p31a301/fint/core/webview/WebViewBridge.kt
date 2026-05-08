package com.s14p31a301.fint.core.webview

import android.webkit.JavascriptInterface

/**
 * 웹 → Native 통신 브리지.
 * 웹에서 window.Android.* 로 호출.
 */
class WebViewBridge(
    private val onSaveAuthToken: (accessToken: String, refreshToken: String) -> Unit,
    private val onOpenBusinessCardScanner: () -> Unit,
    private val onOpenDeviceContactPicker: () -> Unit,
    private val onOpenMeetingRecorder: (activityId: Long?) -> Unit,
) {
    @JavascriptInterface
    fun saveAuthToken(accessToken: String, refreshToken: String) {
        onSaveAuthToken(accessToken, refreshToken)
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


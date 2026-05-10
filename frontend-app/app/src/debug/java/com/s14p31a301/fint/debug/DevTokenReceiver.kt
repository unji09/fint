package com.s14p31a301.fint.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.s14p31a301.fint.core.datastore.TokenDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

/**
 * 디버그 전용 BroadcastReceiver.
 *
 * adb 로 JWT 를 직접 주입/조회/삭제할 수 있도록 한다.
 * (debug 빌드에만 포함됨 — `app/src/debug/` 소스셋)
 *
 * ## 사용법
 *
 * ### 1) 토큰 저장 (로그인 우회)
 * ```
 * adb shell am broadcast -a com.s14p31a301.fint.DEV_SET_TOKEN \
 *   --es access "<accessJWT>" --es refresh "<refreshJWT>" \
 *   -p com.s14p31a301.fint
 * ```
 *
 * ### 2) 토큰 확인 (Logcat 으로 출력)
 * ```
 * adb shell am broadcast -a com.s14p31a301.fint.DEV_GET_TOKEN -p com.s14p31a301.fint
 * adb logcat -s DevToken:*
 * ```
 *
 * ### 3) 토큰 삭제 (로그아웃)
 * ```
 * adb shell am broadcast -a com.s14p31a301.fint.DEV_CLEAR_TOKEN -p com.s14p31a301.fint
 * ```
 */
class DevTokenReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val store: TokenDataStore = GlobalContext.get().get()
        val scope = CoroutineScope(Dispatchers.IO)
        when (intent.action) {
            ACTION_SET -> {
                val access = intent.getStringExtra(EXTRA_ACCESS).orEmpty()
                val refresh = intent.getStringExtra(EXTRA_REFRESH).orEmpty()
                if (access.isBlank()) {
                    Log.w(TAG, "SET 실패: --es access <token> 누락")
                    return
                }
                scope.launch {
                    store.saveTokens(access, refresh)
                    Log.i(TAG, "SET ok (access.len=${access.length}, refresh.len=${refresh.length})")
                }
            }
            ACTION_GET -> {
                scope.launch {
                    val a = store.getAccessToken()
                    val r = store.getRefreshToken()
                    Log.i(
                        TAG,
                        "GET access=${a?.take(20)}…(len=${a?.length ?: 0}) " +
                            "refresh=${r?.take(20)}…(len=${r?.length ?: 0})"
                    )
                }
            }
            ACTION_CLEAR -> {
                scope.launch {
                    store.clear()
                    Log.i(TAG, "CLEAR ok")
                }
            }
            else -> Log.w(TAG, "unknown action=${intent.action}")
        }
    }

    companion object {
        private const val TAG = "DevToken"
        const val ACTION_SET = "com.s14p31a301.fint.DEV_SET_TOKEN"
        const val ACTION_GET = "com.s14p31a301.fint.DEV_GET_TOKEN"
        const val ACTION_CLEAR = "com.s14p31a301.fint.DEV_CLEAR_TOKEN"
        const val EXTRA_ACCESS = "access"
        const val EXTRA_REFRESH = "refresh"
    }
}


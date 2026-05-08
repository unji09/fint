package com.s14p31a301.fint.core.notification

/**
 * FCM 메시지 수신 서비스.
 * - onNewToken → 서버에 등록 (POST /notifications/devices)
 * - onMessageReceived → NotificationChannel로 표시 + NotificationRouter로 라우팅
 *
 * AndroidManifest 에 service 등록 필요.
 */
class FintMessagingService {
    // TODO: extends FirebaseMessagingService
}


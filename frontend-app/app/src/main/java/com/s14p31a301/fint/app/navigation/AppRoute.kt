package com.s14p31a301.fint.app.navigation

/**
 * 앱 내 화면 라우트 정의.
 * WebView 기본 + Native 전용 화면(명함/연락처/녹음/알림 권한)만 라우트로 둔다.
 */
sealed class AppRoute(val path: String) {
    data object Web : AppRoute("web")

    data object BusinessCardScan : AppRoute("businesscard/scan")
    data object BusinessCardResult : AppRoute("businesscard/result?imagePath={imagePath}") {
        fun build(imagePath: String): String = "businesscard/result?imagePath=${android.net.Uri.encode(imagePath)}"
    }

    /** 기기 연락처 graph (list + select 가 동일 ViewModelStoreOwner 공유) */
    data object DeviceContactGraph : AppRoute("devicecontact_graph")
    data object DeviceContactList : AppRoute("devicecontact/list")
    data object DeviceContactSelect : AppRoute("devicecontact/select/{contactId}") {
        fun build(contactId: String): String = "devicecontact/select/${android.net.Uri.encode(contactId)}"
    }

    data object MeetingRecording : AppRoute("recording/meeting")
    data object RecordingResult : AppRoute("recording/result")

    data object NotificationPermission : AppRoute("notification/permission")
}

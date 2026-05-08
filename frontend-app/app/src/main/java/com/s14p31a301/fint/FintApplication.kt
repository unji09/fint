package com.s14p31a301.fint

import android.app.Application

/**
 * 앱 전역 진입점.
 * - Koin DI 컨테이너 시작
 * - FCM, NotificationChannel, Logger 등 전역 초기화
 */
class FintApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // TODO: startKoin {
        //     androidContext(this@FintApplication)
        //     modules(networkModule, dataStoreModule, repositoryModule, viewModelModule)
        // }
        // TODO: NotificationChannels.register(this)
        // TODO: FCM 초기화
    }
}

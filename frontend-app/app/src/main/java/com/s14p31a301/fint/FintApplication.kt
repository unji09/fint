package com.s14p31a301.fint

import android.app.Application
import com.s14p31a301.fint.app.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

/**
 * 앱 전역 진입점.
 * - Koin DI 컨테이너 시작
 * - (Phase 7) NotificationChannels, FCM 초기화
 */
class FintApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.ERROR)
            androidContext(this@FintApplication)
            modules(appModules)
        }

        // TODO(Phase 7): NotificationChannels.register(this)
        // TODO(Phase 7): FCM 초기화
    }
}

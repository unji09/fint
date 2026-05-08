package com.s14p31a301.fint.app.di

import com.s14p31a301.fint.core.datastore.TokenDataStore
import com.s14p31a301.fint.core.datastore.TokenDataStoreImpl
import com.s14p31a301.fint.core.network.interceptor.AuthInterceptor
import com.s14p31a301.fint.core.network.interceptor.UnauthorizedInterceptor
import com.s14p31a301.fint.core.network.retrofit.RetrofitClient
import com.s14p31a301.fint.core.network.s3.S3Uploader
import com.s14p31a301.fint.feature.web.WebViewModel
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Phase 3 DI 구성 (Koin).
 *
 * - dataStoreModule  : TokenDataStore
 * - networkModule    : Interceptors / OkHttp / Retrofit / S3Uploader
 * - repositoryModule : feature 별 Repository (Phase 4~7 에서 추가)
 * - viewModelModule  : WebViewModel + feature ViewModel (Phase 4~7 에서 추가)
 */

val dataStoreModule = module {
    single<TokenDataStore> { TokenDataStoreImpl(androidContext()) }
}

val networkModule = module {

    single { AuthInterceptor(tokenProvider = { runBlocking { get<TokenDataStore>().getAccessToken() } }) }

    single { UnauthorizedInterceptor() }

    single {
        RetrofitClient.okHttp(
            authInterceptor = get(),
            unauthorizedInterceptor = get(),
        )
    }

    single { RetrofitClient.retrofit(get()) }

    single { S3Uploader() }
}

val repositoryModule = module {
    // TODO(Phase 4~7): BusinessCardRepository, DeviceContactRepository,
    //                  RecordingRepository, NotificationRepository
}

val viewModelModule = module {
    viewModel { WebViewModel(tokenDataStore = get()) }
    // TODO(Phase 4~7): BusinessCardViewModel, DeviceContactViewModel,
    //                  RecordingViewModel
}

val appModules = listOf(
    dataStoreModule,
    networkModule,
    repositoryModule,
    viewModelModule,
)


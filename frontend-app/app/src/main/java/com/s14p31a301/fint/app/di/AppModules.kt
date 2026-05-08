package com.s14p31a301.fint.app.di

import com.s14p31a301.fint.core.datastore.TokenDataStore
import com.s14p31a301.fint.core.datastore.TokenDataStoreImpl
import com.s14p31a301.fint.core.media.camera.CameraManager
import com.s14p31a301.fint.core.media.file.FileManager
import com.s14p31a301.fint.core.network.interceptor.AuthInterceptor
import com.s14p31a301.fint.core.network.interceptor.UnauthorizedInterceptor
import com.s14p31a301.fint.core.network.retrofit.RetrofitClient
import com.s14p31a301.fint.core.network.s3.S3Uploader
import com.s14p31a301.fint.feature.businesscard.data.remote.BusinessCardApi
import com.s14p31a301.fint.feature.businesscard.data.repository.BusinessCardRepositoryImpl
import com.s14p31a301.fint.feature.businesscard.domain.repository.BusinessCardRepository
import com.s14p31a301.fint.feature.businesscard.domain.usecase.RegisterContactFromBusinessCardUseCase
import com.s14p31a301.fint.feature.businesscard.presentation.BusinessCardViewModel
import com.s14p31a301.fint.feature.devicecontact.data.local.DeviceContactDataSource
import com.s14p31a301.fint.feature.devicecontact.data.local.DeviceContactDataSourceImpl
import com.s14p31a301.fint.feature.devicecontact.data.remote.ContactApi
import com.s14p31a301.fint.feature.devicecontact.data.repository.DeviceContactRepositoryImpl
import com.s14p31a301.fint.feature.devicecontact.domain.repository.DeviceContactRepository
import com.s14p31a301.fint.feature.devicecontact.presentation.DeviceContactViewModel
import com.s14p31a301.fint.feature.web.WebViewModel
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit

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

/**
 * 미디어 (CameraX, 임시 파일) — Phase 4 추가
 */
val mediaModule = module {
    single { CameraManager(androidContext()) }
    single { FileManager(androidContext()) }
}

/**
 * Repository / DataSource — Phase 4~7 점진 추가.
 * ⚠️ 실제 네트워크 호출 본문은 stub (Phase 4: BusinessCard / Phase 5: DeviceContact API 담당자 구현 예정).
 */
val repositoryModule = module {
    // Business Card
    single { get<Retrofit>().create(BusinessCardApi::class.java) }
    single<BusinessCardRepository> { BusinessCardRepositoryImpl(api = get(), s3Uploader = get()) }
    factory { RegisterContactFromBusinessCardUseCase(repository = get()) }

    // Device Contact
    single<DeviceContactDataSource> { DeviceContactDataSourceImpl() }
    single { get<Retrofit>().create(ContactApi::class.java) }
    single<DeviceContactRepository> { DeviceContactRepositoryImpl(local = get(), api = get()) }

    // TODO(Phase 6~7): RecordingRepository, NotificationRepository
}

val viewModelModule = module {
    viewModel { WebViewModel(tokenDataStore = get()) }

    // Phase 4: 명함 OCR — imagePath 는 화면에서 parametersOf 로 전달
    viewModel { (imagePath: String?) ->
        BusinessCardViewModel(imagePath = imagePath, repository = get())
    }

    // Phase 5: 기기 연락처
    viewModel { DeviceContactViewModel(repository = get()) }
}

val appModules = listOf(
    dataStoreModule,
    networkModule,
    mediaModule,
    repositoryModule,
    viewModelModule,
)

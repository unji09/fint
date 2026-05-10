package com.s14p31a301.fint.core.network.retrofit

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.s14p31a301.fint.BuildConfig
import com.s14p31a301.fint.core.network.interceptor.AuthInterceptor
import com.s14p31a301.fint.core.network.interceptor.UnauthorizedInterceptor
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * Retrofit 인스턴스 빌더.
 * - BASE_URL: BuildConfig.API_BASE_URL
 * - OkHttp: AuthInterceptor + UnauthorizedInterceptor + (debug)Logging
 * - Converter: kotlinx.serialization JSON
 */
object RetrofitClient {

    private val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun okHttp(
        authInterceptor: AuthInterceptor,
        unauthorizedInterceptor: UnauthorizedInterceptor,
        extraInterceptors: List<Interceptor> = emptyList(),
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS) // 업로드 고려
            .addInterceptor(authInterceptor)
            .addInterceptor(unauthorizedInterceptor)

        extraInterceptors.forEach(builder::addInterceptor)

        if (BuildConfig.DEBUG) {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
                redactHeader(AuthInterceptor.HEADER_AUTHORIZATION)
            }
            builder.addInterceptor(logging)
        }
        return builder.build()
    }

    fun retrofit(
        client: OkHttpClient,
        baseUrl: String = BuildConfig.API_BASE_URL,
    ): Retrofit {
        val mediaType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
            .client(client)
            .addConverterFactory(json.asConverterFactory(mediaType))
            .build()
    }
}

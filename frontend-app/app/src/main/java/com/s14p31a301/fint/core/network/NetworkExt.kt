package com.s14p31a301.fint.core.network

import com.s14p31a301.fint.core.common.error.AppError
import com.s14p31a301.fint.core.network.dto.ApiResponse
import retrofit2.HttpException
import java.io.IOException

/**
 * Throwable / Retrofit 응답 → AppError 변환.
 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is HttpException -> when (code()) {
        401, 403 -> AppError.Auth(message())
        else -> AppError.Network(code = code(), msg = message())
    }
    is IOException -> AppError.Network(msg = message ?: "Network IO error")
    else -> AppError.Unknown(msg = message)
}

/**
 * 서버 공통 응답 언래핑. success=false 거나 data=null 이면 [AppError] 발생.
 */
fun <T> ApiResponse<T>.unwrap(): T {
    if (!success) {
        throw AppError.Network(msg = message ?: "API failed (${errorCode ?: "UNKNOWN"})")
    }
    return data ?: throw AppError.Network(msg = "Empty response body")
}


package com.s14p31a301.fint.core.network

import com.s14p31a301.fint.core.common.error.AppError
import com.s14p31a301.fint.core.network.dto.ApiResponse
import retrofit2.HttpException
import java.io.IOException

// Throwable / Retrofit response -> AppError mapper.
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is HttpException -> when (code()) {
        401, 403 -> AppError.Auth(message())
        else -> AppError.Network(code = code(), msg = message())
    }
    is IOException -> AppError.Network(msg = message ?: "Network IO error")
    else -> AppError.Unknown(msg = message)
}

// Unwraps the common API envelope.
//
// Backend specs under mydocs/file_api show response examples like
// { "data": { ... } } without an explicit "success" field, so we are lenient:
//   1) if errorCode is present -> always failure
//   2) if data is non-null     -> success (regardless of "success" field)
//   3) otherwise (data null)   -> failure
fun <T> ApiResponse<T>.unwrap(): T {
    if (errorCode != null) {
        throw AppError.Network(msg = message ?: "API failed ($errorCode)")
    }
    return data ?: throw AppError.Network(
        msg = message ?: "Empty response body (success=$success)"
    )
}


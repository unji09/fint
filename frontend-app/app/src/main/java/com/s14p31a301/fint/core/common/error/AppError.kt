package com.s14p31a301.fint.core.common.error

/**
 * 앱 전역 에러 타입.
 */
sealed class AppError(message: String? = null, cause: Throwable? = null) : Throwable(message, cause) {
    data class Network(val code: Int? = null, val msg: String? = null) : AppError(msg)
    data class Auth(val msg: String? = null) : AppError(msg)
    data class Permission(val permission: String) : AppError("Permission denied: $permission")
    data class Unknown(val msg: String? = null) : AppError(msg)
}


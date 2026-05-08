package com.s14p31a301.fint.core.network.dto

/**
 * 서버 공통 응답 래퍼 등 정의.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?,
)


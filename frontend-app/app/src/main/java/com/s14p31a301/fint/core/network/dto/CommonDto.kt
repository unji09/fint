package com.s14p31a301.fint.core.network.dto

import kotlinx.serialization.Serializable

/**
 * 서버 공통 응답 래퍼.
 *
 * 백엔드 합의 포맷 (예시):
 * {
 *   "success": true,
 *   "data": { ... },
 *   "message": null,
 *   "errorCode": null
 * }
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean = false,
    val data: T? = null,
    val message: String? = null,
    val errorCode: String? = null,
)

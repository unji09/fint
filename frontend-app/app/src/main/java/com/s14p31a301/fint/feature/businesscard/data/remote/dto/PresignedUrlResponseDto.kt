package com.s14p31a301.fint.feature.businesscard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * POST /files/presigned-url — 응답.
 *
 * 명세: mydocs/file_api/Presigned URL 발급.md
 *
 * 클라이언트는 [uploadUrl] 로 PUT 업로드
 * (Content-Type 일치 + `x-amz-server-side-encryption: aws:kms` 헤더 필수).
 */
@Serializable
data class PresignedUrlResponseDto(
    val uploadUrl: String,
    val fileKey: String,
    val expiresIn: Int,
    val uploadType: String,   // SINGLE
)


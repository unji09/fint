package com.s14p31a301.fint.feature.businesscard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * POST /files/presigned-url — 요청.
 *
 * 명세: mydocs/file_api/Presigned URL 발급.md
 *
 * - fileType: IMAGE / AUDIO
 * - purpose : MEETING_RECORD / OCR
 */
@Serializable
data class PresignedUrlRequestDto(
    val fileName: String,
    val contentType: String,
    val fileType: String,
    val purpose: String,
    val fileSize: Long? = null,
)


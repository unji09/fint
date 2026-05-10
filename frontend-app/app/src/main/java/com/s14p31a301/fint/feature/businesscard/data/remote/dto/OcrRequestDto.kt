package com.s14p31a301.fint.feature.businesscard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * POST /contacts/ocr — 요청.
 *
 * 명세: mydocs/file_api/명함 OCR 추출.md
 *
 * presigned URL(`purpose=OCR`)로 S3 업로드 완료된 객체의 fileKey 전달.
 */
@Serializable
data class OcrRequestDto(
    val fileKey: String,
)


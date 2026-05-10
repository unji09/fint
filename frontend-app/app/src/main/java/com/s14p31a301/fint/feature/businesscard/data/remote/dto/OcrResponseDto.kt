package com.s14p31a301.fint.feature.businesscard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * POST /contacts/ocr — 응답.
 *
 * 명세: mydocs/file_api/명함 OCR 추출.md
 *
 * 5필드 (이름/직책/전화/이메일/고객사) — 모두 nullable.
 * `account.name` 이 회사명에 해당.
 */
@Serializable
data class OcrAccountDto(
    val name: String? = null,
)

@Serializable
data class OcrResponseDto(
    val name: String? = null,
    val title: String? = null,          // 직책
    val phone: String? = null,
    val email: String? = null,
    val account: OcrAccountDto? = null, // 고객사
)


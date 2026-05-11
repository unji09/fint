package com.s14p31a301.fint.feature.businesscard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * POST /contacts — 요청.
 *
 * 담당자(연락처) 등록. 명함 OCR / 기기 연락처 / 수동 입력 공통.
 */
@Serializable
data class CreateContactRequestDto(
    val name: String,
    val company: String? = null,
    val position: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    /** 명함 이미지 S3 fileKey (OCR 경로). 기기 연락처/수동 등록 시 null. */
    val sourceFileKey: String? = null,
    /** 등록 출처: BUSINESS_CARD / DEVICE_CONTACT / MANUAL */
    val source: String? = null,
)


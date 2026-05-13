package com.s14p31a301.fint.feature.businesscard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * POST /contacts/ocr/init — 명함 OCR 기반 담당자 등록 요청.
 *
 * 서버가 accountName 으로 Account 를 조회/생성한 뒤 담당자를 등록한다.
 */
@Serializable
data class CreateContactRequestDto(
    val accountName: String,
    val name: String,
    val title: String? = null,
    val phone: String? = null,
    val email: String? = null,
)


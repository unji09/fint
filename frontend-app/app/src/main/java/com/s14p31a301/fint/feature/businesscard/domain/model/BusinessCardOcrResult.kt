package com.s14p31a301.fint.feature.businesscard.domain.model

/**
 * 명함 OCR 결과.
 */
data class BusinessCardOcrResult(
    val name: String?,
    val company: String?,
    val position: String?,
    val phone: String?,
    val email: String?,
    val address: String?,
    val rawText: String?,
    val imageFileKey: String?,
)


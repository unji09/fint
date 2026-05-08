package com.s14p31a301.fint.feature.businesscard.data.remote.dto

data class OcrRequestDto(val fileKey: String)

data class OcrResponseDto(
    val name: String?,
    val company: String?,
    val position: String?,
    val phone: String?,
    val email: String?,
    val address: String?,
    val rawText: String?,
)

data class CreateContactRequestDto(
    val name: String,
    val company: String?,
    val position: String?,
    val phone: String?,
    val email: String?,
    val sourceFileKey: String?,
)


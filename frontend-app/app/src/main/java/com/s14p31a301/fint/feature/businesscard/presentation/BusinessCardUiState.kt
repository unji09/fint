package com.s14p31a301.fint.feature.businesscard.presentation

import com.s14p31a301.fint.feature.businesscard.domain.model.BusinessCardOcrResult

data class BusinessCardUiState(
    val isCapturing: Boolean = false,
    val isUploading: Boolean = false,
    val isOcrInProgress: Boolean = false,
    val ocrResult: BusinessCardOcrResult? = null,
    val error: String? = null,
    val registeredContactId: Long? = null,
)


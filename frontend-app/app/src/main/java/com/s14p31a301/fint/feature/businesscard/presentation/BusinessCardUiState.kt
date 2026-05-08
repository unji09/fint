package com.s14p31a301.fint.feature.businesscard.presentation

import com.s14p31a301.fint.feature.businesscard.domain.model.BusinessCardOcrResult

/**
 * 명함 OCR 화면 상태.
 *
 * 화면 단계:
 *   Capture → Confirm(폼 편집) → Saving(loading) → Done(완료) → 종료(callback)
 */
data class BusinessCardUiState(
    val phase: Phase = Phase.Confirm,
    val imagePath: String? = null,
    val isOcrInProgress: Boolean = false,
    val ocrResult: BusinessCardOcrResult? = null,
    val form: ContactForm = ContactForm(),
    val error: String? = null,
    val registeredContactId: Long? = null,
) {
    enum class Phase { Confirm, Saving, Done }
}

data class ContactForm(
    val name: String = "",
    val company: String = "",
    val position: String = "",
    val phone: String = "",
    val email: String = "",
)

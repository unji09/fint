package com.s14p31a301.fint.feature.businesscard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * POST /contacts/ocr/init — 응답.
 */
@Serializable
data class CreateContactResponseDto(
    val contactId: Long,
    val accountId: Long,
    val name: String? = null,
    val title: String? = null,
    val phone: String? = null,
    val email: String? = null,
)


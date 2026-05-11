package com.s14p31a301.fint.feature.businesscard.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * POST /contacts — 응답.
 */
@Serializable
data class CreateContactResponseDto(
    val contactId: Long,
)


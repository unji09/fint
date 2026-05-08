package com.s14p31a301.fint.feature.devicecontact.domain.model

/**
 * 기기 주소록에서 읽어온 연락처.
 */
data class DeviceContact(
    val id: String,
    val name: String,
    val phone: String?,
    val email: String?,
)


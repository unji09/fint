package com.s14p31a301.fint.feature.devicecontact.data.local

import com.s14p31a301.fint.feature.devicecontact.domain.model.DeviceContact

/**
 * ContactsContract 기반 기기 주소록 조회.
 * READ_CONTACTS 권한 필요.
 */
interface DeviceContactDataSource {
    suspend fun loadAll(): List<DeviceContact>
    suspend fun search(query: String): List<DeviceContact>
}


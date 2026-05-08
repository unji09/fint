package com.s14p31a301.fint.feature.devicecontact.domain.repository

import com.s14p31a301.fint.feature.devicecontact.domain.model.DeviceContact

/**
 * 기기 연락처 + 서버 담당자 등록 Repository.
 * 실제 구현은 Phase 5 담당자.
 */
interface DeviceContactRepository {
    suspend fun loadAll(): Result<List<DeviceContact>>
    suspend fun search(query: String): Result<List<DeviceContact>>
    suspend fun registerContact(contact: DeviceContact): Result<Long>
}


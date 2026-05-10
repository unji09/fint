package com.s14p31a301.fint.feature.devicecontact.data.repository

import com.s14p31a301.fint.feature.devicecontact.data.local.DeviceContactDataSource
import com.s14p31a301.fint.feature.devicecontact.data.remote.ContactApi
import com.s14p31a301.fint.feature.devicecontact.domain.model.DeviceContact
import com.s14p31a301.fint.feature.devicecontact.domain.repository.DeviceContactRepository

/**
 * ⚠️ 본문은 Phase 5 데이터 담당자 구현. 현재는 stub.
 */
class DeviceContactRepositoryImpl(
    private val local: DeviceContactDataSource,
    private val api: ContactApi,
) : DeviceContactRepository {

    override suspend fun loadAll(): Result<List<DeviceContact>> = runCatching { local.loadAll() }

    override suspend fun search(query: String): Result<List<DeviceContact>> =
        runCatching { local.search(query) }

    override suspend fun registerContact(contact: DeviceContact): Result<Long> {
        // TODO(API): POST /contacts
        return Result.failure(NotImplementedError("DeviceContactRepository.registerContact — Phase 5 API 담당자 구현 필요"))
    }
}

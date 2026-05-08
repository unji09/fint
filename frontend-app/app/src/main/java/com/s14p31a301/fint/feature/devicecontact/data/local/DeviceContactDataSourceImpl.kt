package com.s14p31a301.fint.feature.devicecontact.data.local

import com.s14p31a301.fint.feature.devicecontact.domain.model.DeviceContact

/**
 * ContactsContract 기반 구현.
 *
 * ⚠️ 실제 ContactsContract 쿼리는 Phase 5 데이터 담당자 구현.
 * 현재는 화면 흐름 점검용 더미 데이터 반환.
 */
class DeviceContactDataSourceImpl : DeviceContactDataSource {

    private val dummy = listOf(
        DeviceContact("1", "이민정", "010-4567-8901", "mj.lee@samsung.com"),
        DeviceContact("2", "김담당", "010-1234-5678", "kim@example.com"),
        DeviceContact("3", "박팀장", "010-9876-5432", "park@example.com"),
        DeviceContact("4", "최부장", "010-2222-3333", null),
        DeviceContact("5", "정대리", "010-7777-8888", "jung@example.com"),
    )

    override suspend fun loadAll(): List<DeviceContact> = dummy

    override suspend fun search(query: String): List<DeviceContact> =
        if (query.isBlank()) dummy
        else dummy.filter { it.name.contains(query, ignoreCase = true) || it.phone?.contains(query) == true }
}


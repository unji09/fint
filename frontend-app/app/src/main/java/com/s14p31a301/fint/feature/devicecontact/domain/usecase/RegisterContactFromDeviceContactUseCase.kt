package com.s14p31a301.fint.feature.devicecontact.domain.usecase

import com.s14p31a301.fint.feature.devicecontact.domain.model.DeviceContact

class RegisterContactFromDeviceContactUseCase {
    suspend operator fun invoke(contact: DeviceContact): Result<Long> {
        // TODO: ContactRepository.register(contact)
        return Result.failure(NotImplementedError())
    }
}


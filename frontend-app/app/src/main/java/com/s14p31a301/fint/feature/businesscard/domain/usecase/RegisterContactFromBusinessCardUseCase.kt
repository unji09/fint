package com.s14p31a301.fint.feature.businesscard.domain.usecase

import com.s14p31a301.fint.feature.businesscard.domain.model.BusinessCardOcrResult

/**
 * 명함 OCR 결과 → contacts 등록.
 */
class RegisterContactFromBusinessCardUseCase {
    suspend operator fun invoke(result: BusinessCardOcrResult): Result<Long> {
        // TODO: BusinessCardRepository.registerContact(result)
        return Result.failure(NotImplementedError())
    }
}


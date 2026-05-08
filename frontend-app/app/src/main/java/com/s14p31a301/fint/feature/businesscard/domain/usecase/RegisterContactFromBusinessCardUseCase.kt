package com.s14p31a301.fint.feature.businesscard.domain.usecase

import com.s14p31a301.fint.feature.businesscard.domain.model.BusinessCardOcrResult
import com.s14p31a301.fint.feature.businesscard.domain.repository.BusinessCardRepository

/**
 * 명함 OCR 결과 → contacts 등록.
 */
class RegisterContactFromBusinessCardUseCase(
    private val repository: BusinessCardRepository,
) {
    suspend operator fun invoke(result: BusinessCardOcrResult): Result<Long> =
        repository.registerContact(result)
}

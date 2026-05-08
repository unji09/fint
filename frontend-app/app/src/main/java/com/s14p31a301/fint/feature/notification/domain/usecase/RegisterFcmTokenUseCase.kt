package com.s14p31a301.fint.feature.notification.domain.usecase

/**
 * FCM 토큰을 서버에 등록 (POST /notifications/devices).
 */
class RegisterFcmTokenUseCase {
    suspend operator fun invoke(token: String): Result<Unit> {
        // TODO
        return Result.failure(NotImplementedError())
    }
}


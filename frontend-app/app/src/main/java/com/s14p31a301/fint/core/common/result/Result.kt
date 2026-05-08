package com.s14p31a301.fint.core.common.result

/**
 * 도메인 레이어 공통 결과 타입.
 */
sealed interface Result<out T> {
    data class Success<T>(val data: T) : Result<T>
    data class Failure(val error: Throwable) : Result<Nothing>
    data object Loading : Result<Nothing>
}


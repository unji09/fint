package com.ssafy.fint.global.exception;

import lombok.Getter;

/**
 * 도메인 로직에서 발생하는 모든 예외의 단일 타입.
 *
 * 사용 예:
 *   throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
 *   throw new BusinessException(UserErrorCode.USER_NOT_FOUND, "userId=" + id);
 *
 * - 첫 번째 생성자: ErrorCode 의 기본 메시지 사용
 * - 두 번째 생성자: 메시지 오버라이드 (로그/응답에서 식별자 등 동적 정보 포함)
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}

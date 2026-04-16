package com.ssafy.fint.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 도메인에 종속되지 않는 공통 에러 코드.
 * 도메인 특화 에러는 각 도메인 패키지의 `<Domain>ErrorCode` enum 으로 정의한다.
 */
@Getter
@RequiredArgsConstructor
public enum CommonErrorCode implements ErrorCode {

    // 400
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "Invalid input format"),
    ILLEGAL_ARGUMENT(HttpStatus.BAD_REQUEST, "C002", "Illegal argument"),

    // 401
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "C101", "Unauthorized"),

    // 403
    FORBIDDEN(HttpStatus.FORBIDDEN, "C201", "Forbidden"),

    // 404
    NOT_FOUND(HttpStatus.NOT_FOUND, "C301", "Resource not found"),

    // 409
    CONFLICT(HttpStatus.CONFLICT, "C401", "Resource conflict"),

    // 5xx
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C500", "Internal server error"),
    EXTERNAL_API_FAILED(HttpStatus.BAD_GATEWAY, "C502", "외부 API 호출에 실패했습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}

package com.ssafy.fint.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "AU001", "아이디 또는 비밀번호가 올바르지 않습니다."),
    COMPANY_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AU002", "존재하지 않는 회사 코드입니다."),
    ACCOUNT_DELETED(HttpStatus.UNAUTHORIZED, "AU003", "삭제된 계정입니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "AU004", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "AU005", "만료된 토큰입니다."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "AU006", "유효하지 않은 리프레시 토큰입니다."),
    ALREADY_LOGGED_OUT(HttpStatus.UNAUTHORIZED, "AU007", "이미 로그아웃된 토큰입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

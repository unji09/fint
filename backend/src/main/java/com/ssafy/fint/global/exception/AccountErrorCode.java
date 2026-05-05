package com.ssafy.fint.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AccountErrorCode implements ErrorCode {

    CONTACT_NOT_FOUND(HttpStatus.NOT_FOUND, "AN301", "존재하지 않거나 해당 고객사 소속이 아닌 담당자입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

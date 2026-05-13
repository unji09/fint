package com.ssafy.fint.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AiErrorCode implements ErrorCode {

    AI_SUGGESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "AI301", "존재하지 않거나 해당 고객사 소속이 아닌 AI 제안입니다."),
    INVALID_AI_RESPONSE(HttpStatus.BAD_GATEWAY, "AI302", "AI 서비스 응답이 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

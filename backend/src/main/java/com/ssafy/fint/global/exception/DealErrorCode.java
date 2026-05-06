package com.ssafy.fint.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DealErrorCode implements ErrorCode {

    ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "DL301", "존재하지 않는 고객사입니다."),
    TEAM_NOT_FOUND(HttpStatus.NOT_FOUND, "DL302", "존재하지 않는 팀입니다."),
    DEAL_NOT_FOUND(HttpStatus.NOT_FOUND, "DL303", "존재하지 않는 딜입니다."),
    PIPELINE_STAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "DL304", "존재하지 않는 파이프라인 단계입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

package com.ssafy.fint.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ActivityErrorCode implements ErrorCode {

    INVALID_TIME_RANGE(HttpStatus.BAD_REQUEST, "AC001", "endAt 은 startAt 이후여야 합니다."),
    DEAL_NOT_FOUND(HttpStatus.NOT_FOUND, "AC301", "연결 가능한 딜을 찾을 수 없습니다."),
    PIPELINE_STAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "AC302", "연결 가능한 파이프라인 스테이지를 찾을 수 없습니다."),
    ACTIVITY_NOT_FOUND(HttpStatus.NOT_FOUND, "AC303", "활동을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

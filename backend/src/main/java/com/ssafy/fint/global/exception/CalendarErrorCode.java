package com.ssafy.fint.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CalendarErrorCode implements ErrorCode {

    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "CL001", "endDate 는 startDate 이후여야 합니다."),
    INVALID_EVENT_ID(HttpStatus.BAD_REQUEST, "CL002", "eventId 형식이 올바르지 않습니다."),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CL003", "해당 일정을 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

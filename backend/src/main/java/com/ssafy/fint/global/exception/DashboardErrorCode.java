package com.ssafy.fint.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DashboardErrorCode implements ErrorCode {

    DASHBOARD_ACCESS_DENIED(HttpStatus.FORBIDDEN, "DA201", "해당 대시보드에 접근할 권한이 없습니다."),
    DASHBOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "DA301", "존재하지 않는 대시보드입니다."),
    WIDGET_NOT_FOUND(HttpStatus.NOT_FOUND, "DA303", "존재하지 않거나 해당 대시보드 소속이 아닌 위젯입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

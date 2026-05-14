package com.ssafy.fint.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DashboardErrorCode implements ErrorCode {

    DASHBOARD_ACCESS_DENIED(HttpStatus.FORBIDDEN, "DA201", "해당 대시보드에 접근할 권한이 없습니다."),
    DASHBOARD_NOT_FOUND(HttpStatus.NOT_FOUND, "DA301", "존재하지 않는 대시보드입니다."),
    WIDGET_NOT_FOUND(HttpStatus.NOT_FOUND, "DA303", "존재하지 않거나 해당 대시보드 소속이 아닌 위젯입니다."),
    INVALID_THUMBNAIL_KEY(HttpStatus.BAD_REQUEST, "DA101", "유효하지 않은 썸네일 키입니다."),
    THUMBNAIL_NOT_FOUND_ON_S3(HttpStatus.BAD_REQUEST, "DA102", "S3에 해당 썸네일 파일이 존재하지 않습니다."),
    QUERY_NOT_SELECT(HttpStatus.BAD_REQUEST, "DA103", "SELECT 쿼리만 실행할 수 있습니다."),
    QUERY_EXECUTION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "DA501", "데이터 조회 쿼리 실행에 실패했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}

package com.ssafy.fint.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 모든 에러 코드 enum 이 구현해야 하는 공통 인터페이스.
 * 도메인별로 `<Domain>ErrorCode` enum 을 만들고 이 인터페이스를 구현한다.
 *
 * code 네이밍 규칙:
 * - 공통: C001, C002, ...
 * - 도메인 앞글자 2자: AU (auth), TN (tenant), US (user), DL (deal), MT (meeting) ...
 * - 예: AU001, AU002, TN001 ...
 */
public interface ErrorCode {

    HttpStatus getStatus();

    String getCode();

    String getMessage();
}

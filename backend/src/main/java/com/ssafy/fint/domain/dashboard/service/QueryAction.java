package com.ssafy.fint.domain.dashboard.service;

/**
 * 자연어 쿼리 요청 시 위젯 처리 방식. AI 스키마의 {@code action} 필드와 1:1 대응.
 */
public enum QueryAction {
    CREATE,
    ADD,
    MODIFY
}
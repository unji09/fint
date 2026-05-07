package com.ssafy.fint.domain.dashboard.dto;

import java.util.Map;

/**
 * SSE {@code event: complete} 페이로드.
 * 처리 완료 시 1회 push (이 시점에 widget+query DB INSERT 완료됨).
 * {@code position} 은 별도 위치/크기 API 가 채울 때까지 null 로 push 한다.
 */
public record QueryStreamCompleteEvent(
        Long widgetId,
        Long queryId,
        String widgetType,
        String title,
        Map<String, Object> config,
        Map<String, Object> position,
        Result result
) {
    public record Result(
            Map<String, Object> data,
            String insightText
    ) {
    }
}

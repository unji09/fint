package com.ssafy.fint.domain.dashboard.dto;

/**
 * SSE {@code event: error} 페이로드.
 * 처리 실패 시 1회 push 후 SSE 종료.
 */
public record QueryStreamErrorEvent(
        String step,
        String message
) {
}

package com.ssafy.fint.domain.dashboard.dto;

/**
 * SSE {@code event: progress} 페이로드.
 * 외부 명세 기준 단계명을 사용한다.
 * (FastAPI 내부 status 인 DATA_QUERYING / COMPONENT_BUILDING 은 Spring 단에서 매핑되어 변환)
 */
public record QueryStreamProgressEvent(
        String step,
        Integer stepNumber,
        Integer totalSteps
) {
}

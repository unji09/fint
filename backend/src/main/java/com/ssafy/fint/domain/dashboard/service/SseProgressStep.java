package com.ssafy.fint.domain.dashboard.service;

/**
 * SSE {@code event: progress} 단계명. 외부 명세(자연어_쿼리_진행_SSE.pdf) 기준.
 * FastAPI 가 Redis 에 SET 하는 내부 status 와 일부 이름이 다르므로 매핑이 필요하다.
 *
 * <pre>
 * 내부 (AiQueryStatus)            외부 (SseProgressStep)
 * INTENT_PARSING        ↔        INTENT_PARSING       (1단계)
 * DATA_QUERYING         ↔        FETCHING_DATA        (2단계)
 * COMPONENT_BUILDING    ↔        BUILDING_COMPONENT   (3단계)
 * STYLING               ↔        STYLING              (4단계)
 * </pre>
 */
public enum SseProgressStep {
    INTENT_PARSING(1),
    FETCHING_DATA(2),
    BUILDING_COMPONENT(3),
    STYLING(4);

    public static final int TOTAL_STEPS = 4;

    private final int stepNumber;

    SseProgressStep(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    /**
     * FastAPI 내부 status 를 외부 SSE 단계명으로 변환한다.
     * progress 이벤트 대상 4단계가 아닌 status (PENDING/COMPLETED/FAILED) 는 null 반환.
     */
    public static SseProgressStep fromAiStatus(AiQueryStatus status) {
        return switch (status) {
            case INTENT_PARSING -> INTENT_PARSING;
            case DATA_QUERYING -> FETCHING_DATA;
            case COMPONENT_BUILDING -> BUILDING_COMPONENT;
            case STYLING -> STYLING;
            default -> null;
        };
    }
}

package com.ssafy.fint.domain.dashboard.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiQueryStatus → SseProgressStep 매핑 단위 테스트.
 * progress 4단계만 매핑되고 PENDING/COMPLETED/FAILED 는 null 이다.
 */
class SseProgressStepTest {

    @Test
    @DisplayName("INTENT_PARSING → INTENT_PARSING(1)")
    void mapsIntentParsing() {
        SseProgressStep step = SseProgressStep.fromAiStatus(AiQueryStatus.INTENT_PARSING);
        assertThat(step).isEqualTo(SseProgressStep.INTENT_PARSING);
        assertThat(step.getStepNumber()).isEqualTo(1);
    }

    @Test
    @DisplayName("DATA_QUERYING → FETCHING_DATA(2)")
    void mapsDataQuerying() {
        SseProgressStep step = SseProgressStep.fromAiStatus(AiQueryStatus.DATA_QUERYING);
        assertThat(step).isEqualTo(SseProgressStep.FETCHING_DATA);
        assertThat(step.getStepNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("COMPONENT_BUILDING → BUILDING_COMPONENT(3)")
    void mapsComponentBuilding() {
        SseProgressStep step = SseProgressStep.fromAiStatus(AiQueryStatus.COMPONENT_BUILDING);
        assertThat(step).isEqualTo(SseProgressStep.BUILDING_COMPONENT);
        assertThat(step.getStepNumber()).isEqualTo(3);
    }

    @Test
    @DisplayName("STYLING → STYLING(4)")
    void mapsStyling() {
        SseProgressStep step = SseProgressStep.fromAiStatus(AiQueryStatus.STYLING);
        assertThat(step).isEqualTo(SseProgressStep.STYLING);
        assertThat(step.getStepNumber()).isEqualTo(4);
    }

    @Test
    @DisplayName("PENDING/COMPLETED/FAILED 는 progress 단계가 아니므로 null 반환.")
    void mapsNonProgressStatusesToNull() {
        assertThat(SseProgressStep.fromAiStatus(AiQueryStatus.PENDING)).isNull();
        assertThat(SseProgressStep.fromAiStatus(AiQueryStatus.COMPLETED)).isNull();
        assertThat(SseProgressStep.fromAiStatus(AiQueryStatus.FAILED)).isNull();
    }

    @Test
    @DisplayName("TOTAL_STEPS 는 4 (progress 단계 수와 일치).")
    void totalStepsIsFour() {
        assertThat(SseProgressStep.TOTAL_STEPS).isEqualTo(4);
    }
}

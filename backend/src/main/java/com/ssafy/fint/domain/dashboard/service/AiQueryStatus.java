package com.ssafy.fint.domain.dashboard.service;

/**
 * FastAPI 가 Redis 에 SET 하는 status 값. (internal-api-spec §2)
 * Spring 시작 API 가 발급하는 PENDING 도 포함된다.
 */
public enum AiQueryStatus {
    PENDING,
    INTENT_PARSING,
    DATA_QUERYING,
    COMPONENT_BUILDING,
    STYLING,
    COMPLETED,
    FAILED
}

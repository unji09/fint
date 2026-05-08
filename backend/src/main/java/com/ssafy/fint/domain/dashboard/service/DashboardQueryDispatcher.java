package com.ssafy.fint.domain.dashboard.service;

/**
 * 자연어 쿼리 작업을 비동기 처리 주체(FastAPI) 에 위임하는 포트.
 *
 * <p>구현체는 internal-api-spec.md §2 에 정의된 {@code POST /api/v1/dashboard/query}
 * 호출을 수행하며, 즉시 응답(traceId)을 받고 실제 처리는 FastAPI 의 background task 에서 이뤄진다.
 */
public interface DashboardQueryDispatcher {

    void dispatch(DashboardQueryDispatchCommand command);
}

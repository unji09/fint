package com.ssafy.fint.domain.dashboard.service;

import java.util.List;

/**
 * 자연어 쿼리 처리를 FastAPI 에 위임하기 위한 도메인 명령.
 * Service 가 만들어 {@link DashboardQueryDispatcher} 로 넘기며,
 * 어댑터에서 FastAPI 호출 페이로드로 변환된다.
 */
public record DashboardQueryDispatchCommand(
        String traceId,
        String action,
        String inputText,
        Long dashboardId,
        Long userId,
        Long tenantId,
        List<Object> existingWidgets,
        Object currentWidget
) {
}

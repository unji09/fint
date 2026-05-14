package com.ssafy.fint.domain.dashboard.dto;

import com.ssafy.fint.domain.dashboard.entity.DashboardQuery;

import java.time.OffsetDateTime;
import java.util.Map;

public record QueryHistoryResponse(
        Long queryId,
        String inputText,
        Map<String, Object> result,
        OffsetDateTime completedAt
) {

    public static QueryHistoryResponse from(DashboardQuery query) {
        return new QueryHistoryResponse(
                query.getDashboardQueryId(),
                query.getInputText(),
                query.getResult(),
                query.getCompletedAt()
        );
    }
}

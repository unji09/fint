package com.ssafy.fint.domain.dashboard.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.fint.domain.dashboard.service.DashboardQueryDispatchCommand;

import java.util.List;

/**
 * Spring → FastAPI {@code POST /api/v1/dashboard/query} 요청 바디.
 */
public record AiQueryDispatchRequest(
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("action") String action,
        @JsonProperty("input_text") String inputText,
        @JsonProperty("dashboard_id") Long dashboardId,
        @JsonProperty("tenant_id") Long tenantId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("existing_widgets") List<Object> existingWidgets,
        @JsonProperty("current_widget") Object currentWidget
) {

    public static AiQueryDispatchRequest from(DashboardQueryDispatchCommand command) {
        return new AiQueryDispatchRequest(
                command.traceId(),
                command.action(),
                command.inputText(),
                command.dashboardId(),
                command.tenantId(),
                command.userId(),
                command.existingWidgets(),
                command.currentWidget()
        );
    }
}

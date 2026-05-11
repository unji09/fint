package com.ssafy.fint.domain.dashboard.dto;

import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;

import java.util.List;
import java.util.Map;

public record DashboardDetailResponse(
        Long dashboardId,
        String title,
        List<WidgetDetail> widgets
) {
    public static DashboardDetailResponse of(Dashboard dashboard, List<DashboardWidget> widgets) {
        return new DashboardDetailResponse(
                dashboard.getDashboardId(),
                dashboard.getTitle(),
                widgets.stream().map(WidgetDetail::from).toList()
        );
    }

    public record WidgetDetail(
            Long widgetId,
            String widgetType,
            String title,
            Map<String, Object> config,
            Map<String, Object> position,
            Long queryId,
            String inputText,
            Map<String, Object> result
    ) {
        public static WidgetDetail from(DashboardWidget widget) {
            var query = widget.getDashboardQuery();
            return new WidgetDetail(
                    widget.getDashboardWidgetId(),
                    widget.getWidgetType().name(),
                    widget.getTitle(),
                    widget.getConfig(),
                    widget.getPosition(),
                    query != null ? query.getDashboardQueryId() : null,
                    query != null ? query.getInputText() : null,
                    query != null ? query.getResult() : null
            );
        }
    }
}

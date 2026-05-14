package com.ssafy.fint.domain.dashboard.dto;

import java.util.List;

public record WidgetCreateResponse(
        List<Long> widgetIds
) {
    public static WidgetCreateResponse of(List<Long> widgetIds) {
        return new WidgetCreateResponse(widgetIds);
    }
}

package com.ssafy.fint.domain.dashboard.dto;

import com.ssafy.fint.domain.dashboard.entity.DashboardTemplate;

import java.util.List;
import java.util.Map;

public record DashboardTemplateGroupResponse(
        Long groupId,
        List<TemplateWidget> widgets
) {
    public record TemplateWidget(
            Long templateId,
            String widgetType,
            String title,
            Map<String, Object> config,
            Map<String, Object> position
    ) {
        public static TemplateWidget from(DashboardTemplate template) {
            return new TemplateWidget(
                    template.getDashboardTemplateId(),
                    template.getWidgetType().name(),
                    template.getTitle(),
                    template.getConfig(),
                    template.getPosition()
            );
        }
    }
}

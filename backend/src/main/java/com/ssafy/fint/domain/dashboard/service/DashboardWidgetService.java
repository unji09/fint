package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.WidgetCreateResponse;
import com.ssafy.fint.domain.dashboard.dto.WidgetUpdateRequest;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardTemplate;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import com.ssafy.fint.domain.dashboard.repository.DashboardQueryRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardTemplateRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardWidgetRepository;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import com.ssafy.fint.global.exception.DashboardErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 대시보드 위젯 생성/수정/삭제.
 * 생성: 템플릿 단건 복사 또는 그룹 일괄 복사.
 * 수정: title/config/position 중 제공된 필드만 dirty checking 으로 부분 업데이트.
 */
@Service
@RequiredArgsConstructor
public class DashboardWidgetService {

    private static final int TEMPLATE_GROUP_SIZE = 8;

    private final DashboardRepository dashboardRepository;
    private final DashboardWidgetRepository dashboardWidgetRepository;
    private final DashboardQueryRepository dashboardQueryRepository;
    private final DashboardTemplateRepository dashboardTemplateRepository;

    @Transactional
    public WidgetCreateResponse addFromTemplate(CustomUserDetails me, Long dashboardId, Long templateId) {
        Dashboard dashboard = findDashboardAndCheckOwner(me, dashboardId);

        DashboardTemplate template = dashboardTemplateRepository.findById(templateId)
                .orElseThrow(() -> new BusinessException(DashboardErrorCode.TEMPLATE_NOT_FOUND));

        DashboardWidget widget = dashboardWidgetRepository.save(toWidget(template, dashboard));
        return WidgetCreateResponse.of(List.of(widget.getDashboardWidgetId()));
    }

    @Transactional
    public WidgetCreateResponse addFromTemplateGroup(CustomUserDetails me, Long dashboardId, Long templateGroupId) {
        Dashboard dashboard = findDashboardAndCheckOwner(me, dashboardId);

        long startId = (templateGroupId - 1) * TEMPLATE_GROUP_SIZE + 1;
        long endId = templateGroupId * TEMPLATE_GROUP_SIZE;

        List<DashboardTemplate> templates = dashboardTemplateRepository
                .findByDashboardTemplateIdBetween(startId, endId);

        List<DashboardWidget> widgets = templates.stream()
                .map(t -> toWidget(t, dashboard))
                .toList();

        List<Long> ids = dashboardWidgetRepository.saveAll(widgets).stream()
                .map(DashboardWidget::getDashboardWidgetId)
                .toList();

        return WidgetCreateResponse.of(ids);
    }

    @Transactional
    public void update(CustomUserDetails me, Long dashboardId, Long widgetId, WidgetUpdateRequest request) {
        if (request.title() == null && request.config() == null && request.position() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "title/config/position 중 하나 이상 필요합니다.");
        }

        findDashboardAndCheckOwner(me, dashboardId);

        DashboardWidget widget = dashboardWidgetRepository
                .findByDashboardWidgetIdAndDashboard_DashboardId(widgetId, dashboardId)
                .orElseThrow(() -> new BusinessException(DashboardErrorCode.WIDGET_NOT_FOUND));

        if (request.title() != null) {
            widget.changeTitle(request.title());
        }
        if (request.config() != null) {
            widget.changeConfig(request.config());
        }
        if (request.position() != null) {
            widget.changePosition(request.position());
        }
    }

    @Transactional
    public void delete(CustomUserDetails me, Long dashboardId, Long widgetId) {
        findDashboardAndCheckOwner(me, dashboardId);

        DashboardWidget widget = dashboardWidgetRepository
                .findByDashboardWidgetIdAndDashboard_DashboardId(widgetId, dashboardId)
                .orElseThrow(() -> new BusinessException(DashboardErrorCode.WIDGET_NOT_FOUND));

        if (widget.getDashboardQuery() != null) {
            dashboardQueryRepository.delete(widget.getDashboardQuery());
        }
        dashboardWidgetRepository.delete(widget);
    }

    private Dashboard findDashboardAndCheckOwner(CustomUserDetails me, Long dashboardId) {
        Dashboard dashboard = dashboardRepository.findById(dashboardId)
                .orElseThrow(() -> new BusinessException(DashboardErrorCode.DASHBOARD_NOT_FOUND));

        if (!dashboard.getOwner().getUserId().equals(me.getUserId())) {
            throw new BusinessException(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);
        }
        return dashboard;
    }

    private DashboardWidget toWidget(DashboardTemplate template, Dashboard dashboard) {
        return DashboardWidget.builder()
                .dashboard(dashboard)
                .widgetType(template.getWidgetType())
                .title(template.getTitle())
                .config(template.getConfig())
                .position(template.getPosition())
                .sourceQuery(template.getSourceQuery())
                .build();
    }
}

package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.WidgetUpdateRequest;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import com.ssafy.fint.domain.dashboard.repository.DashboardQueryRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardWidgetRepository;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import com.ssafy.fint.global.exception.DashboardErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 위젯 수정 (드래그/리사이즈/필터/제목 변경 통합).
 * title/config/position 중 제공된 필드만 dirty checking 으로 부분 업데이트한다.
 */
@Service
@RequiredArgsConstructor
public class DashboardWidgetService {

    private final DashboardRepository dashboardRepository;
    private final DashboardWidgetRepository dashboardWidgetRepository;
    private final DashboardQueryRepository dashboardQueryRepository;

    @Transactional
    public void update(CustomUserDetails me, Long dashboardId, Long widgetId, WidgetUpdateRequest request) {
        if (request.title() == null && request.config() == null && request.position() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "title/config/position 중 하나 이상 필요합니다.");
        }

        Dashboard dashboard = dashboardRepository.findById(dashboardId)
                .orElseThrow(() -> new BusinessException(DashboardErrorCode.DASHBOARD_NOT_FOUND));

        if (!dashboard.getOwner().getUserId().equals(me.getUserId())) {
            throw new BusinessException(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);
        }

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
        Dashboard dashboard = dashboardRepository.findById(dashboardId)
                .orElseThrow(() -> new BusinessException(DashboardErrorCode.DASHBOARD_NOT_FOUND));

        if (!dashboard.getOwner().getUserId().equals(me.getUserId())) {
            throw new BusinessException(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);
        }

        DashboardWidget widget = dashboardWidgetRepository
                .findByDashboardWidgetIdAndDashboard_DashboardId(widgetId, dashboardId)
                .orElseThrow(() -> new BusinessException(DashboardErrorCode.WIDGET_NOT_FOUND));

        if (widget.getDashboardQuery() != null) {
            dashboardQueryRepository.delete(widget.getDashboardQuery());
        }
        dashboardWidgetRepository.delete(widget);
    }
}

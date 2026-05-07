package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.DashboardCreateRequest;
import com.ssafy.fint.domain.dashboard.dto.DashboardCreateResponse;
import com.ssafy.fint.domain.dashboard.dto.QueryStartRequest;
import com.ssafy.fint.domain.dashboard.dto.QueryStartResponse;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardTemplateRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardWidgetRepository;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 대시보드 생성 서비스. 빈 / 템플릿 그룹 카피 / 자연어 쿼리 트리거 3분기 통합 진입.
 *
 * <ul>
 *   <li>[templateId, inputText 기준] 둘 다 없음 → dashboards INSERT 만</li>
 *   <li>templateId 있음 → dashboard_templates 의 그룹 N (위젯 {@value #TEMPLATE_GROUP_SIZE} 개) 을
 *       방금 생성한 대시보드의 위젯으로 카피. 그룹 크기는 추후 변동 가능.</li>
 *   <li>inputText 있음 → 빈 대시보드 생성 후 자연어 쿼리 시작 흐름
 *       ({@link DashboardQueryService#start}) 위임. 응답에 traceId 가 포함된다.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String DEFAULT_TITLE = "제목없음";
    private static final int TEMPLATE_GROUP_SIZE = 8;

    private final UserRepository userRepository;
    private final DashboardRepository dashboardRepository;
    private final DashboardTemplateRepository dashboardTemplateRepository;
    private final DashboardWidgetRepository dashboardWidgetRepository;
    private final DashboardQueryService dashboardQueryService;

    @Transactional
    public DashboardCreateResponse create(CustomUserDetails me, DashboardCreateRequest request) {
        User owner = userRepository.findById(me.getUserId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        Dashboard dashboard = dashboardRepository.save(Dashboard.builder()
                .owner(owner)
                .title(resolveTitle(request.title()))
                .build());

        if (request.templateId() != null) {
            applyTemplateGroup(request.templateId(), dashboard);
        }

        String traceId = null;
        if (StringUtils.hasText(request.inputText())) {
            QueryStartResponse started = dashboardQueryService.start(
                    me,
                    dashboard.getDashboardId(),
                    new QueryStartRequest(request.inputText())
            );
            traceId = started.traceId();
        }

        return new DashboardCreateResponse(dashboard.getDashboardId(), traceId);
    }

    private String resolveTitle(String input) {
        return StringUtils.hasText(input) ? input : DEFAULT_TITLE;
    }

    private void applyTemplateGroup(Long templateGroupId, Dashboard dashboard) {
        long startId = (templateGroupId - 1) * TEMPLATE_GROUP_SIZE + 1;
        long endId = templateGroupId * TEMPLATE_GROUP_SIZE;

        List<DashboardWidget> widgets = dashboardTemplateRepository
                .findByDashboardTemplateIdBetween(startId, endId)
                .stream()
                .map(template -> DashboardWidget.builder()
                        .dashboard(dashboard)
                        .widgetType(template.getWidgetType())
                        .title(template.getTitle())
                        .config(template.getConfig())
                        .position(template.getPosition())
                        .build())
                .toList();

        dashboardWidgetRepository.saveAll(widgets);
    }
}

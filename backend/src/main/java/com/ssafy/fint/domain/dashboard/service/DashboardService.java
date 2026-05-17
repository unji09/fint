package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.DashboardCreateRequest;
import com.ssafy.fint.domain.dashboard.dto.DashboardCreateResponse;
import com.ssafy.fint.domain.dashboard.dto.DashboardDetailResponse;
import com.ssafy.fint.domain.dashboard.dto.DashboardListResponse;
import com.ssafy.fint.domain.dashboard.dto.DashboardTemplateGroupResponse;
import com.ssafy.fint.domain.dashboard.dto.DashboardUpdateRequest;
import com.ssafy.fint.domain.dashboard.dto.QueryHistoryResponse;
import com.ssafy.fint.domain.dashboard.dto.QueryStartRequest;
import com.ssafy.fint.domain.dashboard.dto.QueryStartResponse;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import com.ssafy.fint.domain.dashboard.repository.DashboardQueryRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardTemplateRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardWidgetRepository;
import com.ssafy.fint.domain.file.service.FilePresignedService;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.config.properties.AwsS3Properties;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import com.ssafy.fint.global.exception.DashboardErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String DEFAULT_TITLE = "제목없음";
    private static final int TEMPLATE_GROUP_SIZE = 8;

    private final UserRepository userRepository;
    private final DashboardRepository dashboardRepository;
    private final DashboardTemplateRepository dashboardTemplateRepository;
    private final DashboardWidgetRepository dashboardWidgetRepository;
    private final DashboardQueryRepository dashboardQueryRepository;
    private final DashboardQueryService dashboardQueryService;
    private final QueryExecutionService queryExecutionService;
    private final FilePresignedService filePresignedService;
    private final AwsS3Properties awsS3Properties;

    @Transactional(readOnly = true)
    public List<DashboardTemplateGroupResponse> findTemplates(List<Long> groupIds) {
        Map<Long, List<DashboardTemplateGroupResponse.TemplateWidget>> grouped =
                dashboardTemplateRepository.findAll().stream()
                        .collect(Collectors.groupingBy(
                                t -> (t.getDashboardTemplateId() - 1) / TEMPLATE_GROUP_SIZE + 1,
                                Collectors.mapping(
                                        DashboardTemplateGroupResponse.TemplateWidget::from,
                                        Collectors.toList()
                                )
                        ));

        return grouped.entrySet().stream()
                .filter(e -> groupIds == null || groupIds.contains(e.getKey()))
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new DashboardTemplateGroupResponse(e.getKey(), e.getValue()))
                .toList();
    }

    @Transactional
    public DashboardDetailResponse findDetail(CustomUserDetails me, Long dashboardId) {
        Dashboard dashboard = dashboardRepository.findById(dashboardId)
                .orElseThrow(() -> new BusinessException(DashboardErrorCode.DASHBOARD_NOT_FOUND));

        if (!dashboard.getOwner().getUserId().equals(me.getUserId())) {
            throw new BusinessException(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);
        }

        dashboard.touchAccess(OffsetDateTime.now());

        List<DashboardWidget> widgets = dashboardWidgetRepository.findByDashboard(dashboard);
        List<DashboardDetailResponse.WidgetDetail> details = widgets.stream()
                .map(w -> {
                    List<Map<String, Object>> data = null;
                    try {
                        data = executeSourceQuery(w.getSourceQuery(), me.getTenantId());
                    } catch (Exception e) {
                        log.warn("위젯 sourceQuery 실행 실패: widgetId={}, error={}", w.getDashboardWidgetId(), e.getMessage());
                    }
                    return DashboardDetailResponse.WidgetDetail.from(w, data);
                })
                .toList();

        return DashboardDetailResponse.of(dashboard, details);
    }

    @Transactional(readOnly = true)
    public List<DashboardListResponse> findAll(CustomUserDetails me) {
        User owner = userRepository.findById(me.getUserId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        return dashboardRepository.findTop20ByOwnerOrderByLastAccessedAtDesc(owner)
                .stream()
                .map(DashboardListResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<QueryHistoryResponse> findQueryHistory(CustomUserDetails me, Long dashboardId) {
        Dashboard dashboard = dashboardRepository.findById(dashboardId)
                .orElseThrow(() -> new BusinessException(DashboardErrorCode.DASHBOARD_NOT_FOUND));

        if (!dashboard.getOwner().getUserId().equals(me.getUserId())) {
            throw new BusinessException(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);
        }

        return dashboardQueryRepository.findByDashboard_DashboardIdOrderByCompletedAtAsc(dashboardId)
                .stream()
                .map(QueryHistoryResponse::from)
                .toList();
    }

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

    @Transactional
    public void update(CustomUserDetails me, Long dashboardId, DashboardUpdateRequest request) {
        if (request.title() == null && request.thumbnailKey() == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT,
                    "수정할 필드가 하나 이상 필요합니다.");
        }

        Dashboard dashboard = dashboardRepository.findById(dashboardId)
                .orElseThrow(() -> new BusinessException(DashboardErrorCode.DASHBOARD_NOT_FOUND));

        if (!dashboard.getOwner().getUserId().equals(me.getUserId())) {
            throw new BusinessException(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);
        }

        if (request.title() != null) {
            dashboard.changeTitle(request.title());
        }
        if (request.thumbnailKey() != null) {
            validateThumbnailKey(request.thumbnailKey(), dashboardId);
            dashboard.changeThumbnailKey(request.thumbnailKey());
        }
    }

    @Transactional
    public void delete(CustomUserDetails me, Long dashboardId) {
        Dashboard dashboard = dashboardRepository.findById(dashboardId)
                .orElseThrow(() -> new BusinessException(DashboardErrorCode.DASHBOARD_NOT_FOUND));

        if (!dashboard.getOwner().getUserId().equals(me.getUserId())) {
            throw new BusinessException(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);
        }

        dashboardWidgetRepository.deleteByDashboard(dashboard);
        dashboardQueryRepository.deleteByDashboard(dashboard);
        dashboardRepository.delete(dashboard);
    }

    private void validateThumbnailKey(String thumbnailKey, Long dashboardId) {
        String expectedPrefix = awsS3Properties.upload().keyPrefix().thumbnail() + dashboardId + "/";
        if (!thumbnailKey.startsWith(expectedPrefix)) {
            throw new BusinessException(DashboardErrorCode.INVALID_THUMBNAIL_KEY);
        }
        if (!filePresignedService.existsOnS3(thumbnailKey)) {
            throw new BusinessException(DashboardErrorCode.THUMBNAIL_NOT_FOUND_ON_S3);
        }
    }

    private List<Map<String, Object>> executeSourceQuery(String sourceQuery, Long tenantId) {
        if (sourceQuery == null || sourceQuery.isBlank()) {
            return null;
        }
        return queryExecutionService.execute(sourceQuery, tenantId);
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
                        .sourceQuery(template.getSourceQuery())
                        .build())
                .toList();

        dashboardWidgetRepository.saveAll(widgets);
    }
}

package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.WidgetCreateResponse;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardTemplate;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import com.ssafy.fint.domain.dashboard.entity.WidgetType;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardTemplateRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardWidgetRepository;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.DashboardErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 위젯 추가(POST /dashboards/{id}/widgets, /widgets/batch) 단위 테스트.
 * 단건 템플릿 복사 + 다건 그룹 복사 + 권한/존재 검증을 분기별로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DashboardWidgetServiceAddTest {

    private static final long TENANT_ID = 1L;
    private static final long OWNER_USER_ID = 99L;
    private static final long STRANGER_USER_ID = 77L;
    private static final long DASHBOARD_ID = 5L;
    private static final long TEMPLATE_ID = 3L;

    @Mock private DashboardRepository dashboardRepository;
    @Mock private DashboardWidgetRepository dashboardWidgetRepository;
    @Mock private DashboardTemplateRepository dashboardTemplateRepository;

    @InjectMocks private DashboardWidgetService dashboardWidgetService;

    private final CustomUserDetails owner =
            new CustomUserDetails(OWNER_USER_ID, TENANT_ID, "MEMBER");
    private final CustomUserDetails stranger =
            new CustomUserDetails(STRANGER_USER_ID, TENANT_ID, "MEMBER");

    // ── 단건: addFromTemplate ──

    @Test
    @DisplayName("단건 — 템플릿을 위젯으로 복사하고 widgetId 를 반환한다.")
    void addFromTemplateCopiesAllFields() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        DashboardTemplate template = newTemplate(TEMPLATE_ID, WidgetType.CHART, "월별 매출",
                Map.of("chart_type", "bar"), Map.of("x", 0, "y", 0, "w", 6, "h", 4),
                "SELECT month, SUM(amount) FROM deals WHERE tenant_id = :tenantId GROUP BY month");

        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.of(template));
        when(dashboardWidgetRepository.save(any(DashboardWidget.class))).thenAnswer(inv -> {
            DashboardWidget w = inv.getArgument(0);
            ReflectionTestUtils.setField(w, "dashboardWidgetId", 100L);
            return w;
        });

        WidgetCreateResponse res = dashboardWidgetService.addFromTemplate(owner, DASHBOARD_ID, TEMPLATE_ID);

        assertThat(res.widgetIds()).containsExactly(100L);

        ArgumentCaptor<DashboardWidget> captor = ArgumentCaptor.forClass(DashboardWidget.class);
        verify(dashboardWidgetRepository).save(captor.capture());
        DashboardWidget saved = captor.getValue();
        assertThat(saved.getDashboard()).isSameAs(dashboard);
        assertThat(saved.getWidgetType()).isEqualTo(WidgetType.CHART);
        assertThat(saved.getTitle()).isEqualTo("월별 매출");
        assertThat(saved.getConfig()).containsEntry("chart_type", "bar");
        assertThat(saved.getPosition()).containsEntry("w", 6);
        assertThat(saved.getSourceQuery()).contains("tenant_id = :tenantId");
        assertThat(saved.getDashboardQuery()).isNull();
    }

    @Test
    @DisplayName("단건 — 템플릿이 존재하지 않으면 TEMPLATE_NOT_FOUND 로 차단된다.")
    void addFromTemplateNotFound() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardTemplateRepository.findById(TEMPLATE_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardWidgetService.addFromTemplate(owner, DASHBOARD_ID, TEMPLATE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.TEMPLATE_NOT_FOUND);

        verify(dashboardWidgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("단건 — 대시보드가 없으면 DASHBOARD_NOT_FOUND 로 차단된다.")
    void addFromTemplateDashboardNotFound() {
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardWidgetService.addFromTemplate(owner, DASHBOARD_ID, TEMPLATE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_NOT_FOUND);

        verify(dashboardTemplateRepository, never()).findById(any());
    }

    @Test
    @DisplayName("단건 — 소유자가 아니면 DASHBOARD_ACCESS_DENIED 로 차단된다.")
    void addFromTemplateAccessDenied() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        assertThatThrownBy(() -> dashboardWidgetService.addFromTemplate(stranger, DASHBOARD_ID, TEMPLATE_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);

        verify(dashboardTemplateRepository, never()).findById(any());
    }

    // ── 다건: addFromTemplateGroup ──

    @Test
    @DisplayName("다건 — 그룹 1 요청 시 템플릿 1~8 을 위젯으로 일괄 복사한다.")
    void addFromTemplateGroupCopiesEight() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        List<DashboardTemplate> templates = buildTemplateGroup(1, 8);

        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardTemplateRepository.findByDashboardTemplateIdBetween(1L, 8L))
                .thenReturn(templates);
        when(dashboardWidgetRepository.saveAll(anyList())).thenAnswer(inv -> {
            List<DashboardWidget> widgets = inv.getArgument(0);
            for (int i = 0; i < widgets.size(); i++) {
                ReflectionTestUtils.setField(widgets.get(i), "dashboardWidgetId", 100L + i);
            }
            return widgets;
        });

        WidgetCreateResponse res = dashboardWidgetService.addFromTemplateGroup(owner, DASHBOARD_ID, 1L);

        assertThat(res.widgetIds()).hasSize(8);
        assertThat(res.widgetIds().get(0)).isEqualTo(100L);

        verify(dashboardTemplateRepository).findByDashboardTemplateIdBetween(1L, 8L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DashboardWidget>> captor = ArgumentCaptor.forClass(List.class);
        verify(dashboardWidgetRepository).saveAll(captor.capture());
        List<DashboardWidget> saved = captor.getValue();
        assertThat(saved).hasSize(8);
        saved.forEach(w -> assertThat(w.getDashboard()).isSameAs(dashboard));
    }

    @Test
    @DisplayName("다건 — 그룹 2 요청 시 템플릿 9~16 범위를 조회한다.")
    void addFromTemplateGroupQueriesCorrectRange() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardTemplateRepository.findByDashboardTemplateIdBetween(9L, 16L))
                .thenReturn(buildTemplateGroup(9, 16));
        when(dashboardWidgetRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        dashboardWidgetService.addFromTemplateGroup(owner, DASHBOARD_ID, 2L);

        verify(dashboardTemplateRepository).findByDashboardTemplateIdBetween(9L, 16L);
    }

    @Test
    @DisplayName("다건 — 빈 그룹이면 빈 위젯 리스트로 진행된다.")
    void addFromTemplateGroupEmpty() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardTemplateRepository.findByDashboardTemplateIdBetween(1L, 8L))
                .thenReturn(List.of());
        when(dashboardWidgetRepository.saveAll(anyList())).thenReturn(List.of());

        WidgetCreateResponse res = dashboardWidgetService.addFromTemplateGroup(owner, DASHBOARD_ID, 1L);

        assertThat(res.widgetIds()).isEmpty();
    }

    @Test
    @DisplayName("다건 — 소유자가 아니면 DASHBOARD_ACCESS_DENIED 로 차단된다.")
    void addFromTemplateGroupAccessDenied() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        assertThatThrownBy(() -> dashboardWidgetService.addFromTemplateGroup(stranger, DASHBOARD_ID, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);

        verify(dashboardTemplateRepository, never()).findByDashboardTemplateIdBetween(any(), any());
    }

    // ── helpers ──

    private Dashboard newDashboard(long ownerUserId) {
        User user = User.builder()
                .tenant(newTenant())
                .role(UserRole.MEMBER)
                .name("owner")
                .passwordHash("x")
                .build();
        ReflectionTestUtils.setField(user, "userId", ownerUserId);
        Dashboard dashboard = Dashboard.builder()
                .owner(user)
                .title("내 대시보드")
                .build();
        ReflectionTestUtils.setField(dashboard, "dashboardId", DASHBOARD_ID);
        return dashboard;
    }

    private Tenant newTenant() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + TENANT_ID).build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);
        return tenant;
    }

    private DashboardTemplate newTemplate(long id, WidgetType type, String title,
                                           Map<String, Object> config, Map<String, Object> position,
                                           String sourceQuery) {
        DashboardTemplate template = DashboardTemplate.builder()
                .widgetType(type)
                .title(title)
                .config(config)
                .position(position)
                .sourceQuery(sourceQuery)
                .build();
        ReflectionTestUtils.setField(template, "dashboardTemplateId", id);
        return template;
    }

    private List<DashboardTemplate> buildTemplateGroup(long startId, long endId) {
        List<DashboardTemplate> templates = new ArrayList<>();
        IntStream.rangeClosed((int) startId, (int) endId).forEach(id ->
                templates.add(newTemplate(id, WidgetType.CHART, "template-" + id,
                        Map.of("k", "v"), Map.of("x", 0, "y", 0), null)));
        return templates;
    }
}

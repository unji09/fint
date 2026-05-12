package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardQuery;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import com.ssafy.fint.domain.dashboard.entity.WidgetType;
import com.ssafy.fint.domain.dashboard.repository.DashboardQueryRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardWidgetServiceDeleteTest {

    private static final long TENANT_ID = 1L;
    private static final long OWNER_USER_ID = 99L;
    private static final long STRANGER_USER_ID = 77L;
    private static final long DASHBOARD_ID = 5L;
    private static final long WIDGET_ID = 10L;
    private static final long QUERY_ID = 20L;

    @Mock private DashboardRepository dashboardRepository;
    @Mock private DashboardWidgetRepository dashboardWidgetRepository;
    @Mock private DashboardQueryRepository dashboardQueryRepository;

    @InjectMocks private DashboardWidgetService dashboardWidgetService;

    private final CustomUserDetails owner =
            new CustomUserDetails(OWNER_USER_ID, TENANT_ID, "MEMBER");
    private final CustomUserDetails stranger =
            new CustomUserDetails(STRANGER_USER_ID, TENANT_ID, "MEMBER");

    @Test
    @DisplayName("소유자가 위젯을 삭제하면 연결된 쿼리도 함께 삭제된다.")
    void deleteWithQuery() {
        Dashboard dashboard = newDashboard();
        DashboardQuery query = newQuery(dashboard);
        DashboardWidget widget = newWidget(dashboard, query);

        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardWidgetRepository.findByDashboardWidgetIdAndDashboard_DashboardId(WIDGET_ID, DASHBOARD_ID))
                .thenReturn(Optional.of(widget));

        dashboardWidgetService.delete(owner, DASHBOARD_ID, WIDGET_ID);

        verify(dashboardQueryRepository).delete(query);
        verify(dashboardWidgetRepository).delete(widget);
    }

    @Test
    @DisplayName("쿼리가 없는 위젯도 정상 삭제된다.")
    void deleteWithoutQuery() {
        Dashboard dashboard = newDashboard();
        DashboardWidget widget = newWidget(dashboard, null);

        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardWidgetRepository.findByDashboardWidgetIdAndDashboard_DashboardId(WIDGET_ID, DASHBOARD_ID))
                .thenReturn(Optional.of(widget));

        dashboardWidgetService.delete(owner, DASHBOARD_ID, WIDGET_ID);

        verify(dashboardQueryRepository, never()).delete(any());
        verify(dashboardWidgetRepository).delete(widget);
    }

    @Test
    @DisplayName("대시보드가 존재하지 않으면 DASHBOARD_NOT_FOUND 로 차단된다.")
    void dashboardNotFound() {
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardWidgetService.delete(owner, DASHBOARD_ID, WIDGET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_NOT_FOUND);

        verify(dashboardWidgetRepository, never()).delete(any());
    }

    @Test
    @DisplayName("호출자가 소유자가 아니면 DASHBOARD_ACCESS_DENIED 로 차단된다.")
    void forbiddenWhenNotOwner() {
        Dashboard dashboard = newDashboard();
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        assertThatThrownBy(() -> dashboardWidgetService.delete(stranger, DASHBOARD_ID, WIDGET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);

        verify(dashboardWidgetRepository, never()).delete(any());
    }

    @Test
    @DisplayName("위젯이 해당 대시보드에 속하지 않으면 WIDGET_NOT_FOUND 로 차단된다.")
    void widgetNotFound() {
        Dashboard dashboard = newDashboard();
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardWidgetRepository.findByDashboardWidgetIdAndDashboard_DashboardId(WIDGET_ID, DASHBOARD_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardWidgetService.delete(owner, DASHBOARD_ID, WIDGET_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.WIDGET_NOT_FOUND);

        verify(dashboardWidgetRepository, never()).delete(any());
    }

    private Dashboard newDashboard() {
        User user = User.builder()
                .tenant(newTenant())
                .role(UserRole.MEMBER)
                .name("owner")
                .passwordHash("x")
                .build();
        ReflectionTestUtils.setField(user, "userId", OWNER_USER_ID);
        Dashboard dashboard = Dashboard.builder()
                .owner(user)
                .title("테스트 대시보드")
                .build();
        ReflectionTestUtils.setField(dashboard, "dashboardId", DASHBOARD_ID);
        return dashboard;
    }

    private DashboardQuery newQuery(Dashboard dashboard) {
        DashboardQuery query = DashboardQuery.builder()
                .dashboard(dashboard)
                .inputText("테스트 쿼리")
                .result(Map.of())
                .completedAt(java.time.OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(query, "dashboardQueryId", QUERY_ID);
        return query;
    }

    private DashboardWidget newWidget(Dashboard dashboard, DashboardQuery query) {
        DashboardWidget widget = DashboardWidget.builder()
                .dashboard(dashboard)
                .dashboardQuery(query)
                .widgetType(WidgetType.BAR_CHART)
                .title("테스트 위젯")
                .config(Map.of())
                .position(Map.of("x", 0, "y", 0))
                .build();
        ReflectionTestUtils.setField(widget, "dashboardWidgetId", WIDGET_ID);
        return widget;
    }

    private Tenant newTenant() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + TENANT_ID).build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);
        return tenant;
    }
}

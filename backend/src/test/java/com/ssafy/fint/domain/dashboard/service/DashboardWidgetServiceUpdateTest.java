package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.WidgetUpdateRequest;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import com.ssafy.fint.domain.dashboard.entity.WidgetType;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardWidgetRepository;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 위젯 수정(PATCH /dashboards/{dashboardId}/widgets/{widgetId}) 단위 테스트.
 * 부분 업데이트 + 권한 검증 + widget 소속 검증을 분기별로 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DashboardWidgetServiceUpdateTest {

    private static final long TENANT_ID = 1L;
    private static final long OWNER_USER_ID = 99L;
    private static final long STRANGER_USER_ID = 77L;
    private static final long DASHBOARD_ID = 5L;
    private static final long WIDGET_ID = 10L;

    @Mock private DashboardRepository dashboardRepository;
    @Mock private DashboardWidgetRepository dashboardWidgetRepository;

    @InjectMocks private DashboardWidgetService dashboardWidgetService;

    private final CustomUserDetails owner =
            new CustomUserDetails(OWNER_USER_ID, TENANT_ID, "MEMBER");
    private final CustomUserDetails stranger =
            new CustomUserDetails(STRANGER_USER_ID, TENANT_ID, "MEMBER");

    @Test
    @DisplayName("title 만 입력 시 widget 의 title 만 변경된다.")
    void updateOnlyTitle() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        DashboardWidget widget = newWidget(dashboard, "old", Map.of("c", "v0"), Map.of("x", 0));
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardWidgetRepository.findByDashboardWidgetIdAndDashboard_DashboardId(WIDGET_ID, DASHBOARD_ID))
                .thenReturn(Optional.of(widget));

        dashboardWidgetService.update(owner, DASHBOARD_ID, WIDGET_ID,
                new WidgetUpdateRequest("new title", null, null));

        assertThat(widget.getTitle()).isEqualTo("new title");
        assertThat(widget.getConfig()).containsEntry("c", "v0");
        assertThat(widget.getPosition()).containsEntry("x", 0);
    }

    @Test
    @DisplayName("config 만 입력 시 widget 의 config 만 변경된다.")
    void updateOnlyConfig() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        DashboardWidget widget = newWidget(dashboard, "t", Map.of("c", "v0"), Map.of("x", 0));
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardWidgetRepository.findByDashboardWidgetIdAndDashboard_DashboardId(WIDGET_ID, DASHBOARD_ID))
                .thenReturn(Optional.of(widget));

        Map<String, Object> newConfig = Map.of("colors", "#ABC");
        dashboardWidgetService.update(owner, DASHBOARD_ID, WIDGET_ID,
                new WidgetUpdateRequest(null, newConfig, null));

        assertThat(widget.getTitle()).isEqualTo("t");
        assertThat(widget.getConfig()).isEqualTo(newConfig);
        assertThat(widget.getPosition()).containsEntry("x", 0);
    }

    @Test
    @DisplayName("position 만 입력 시 widget 의 position 만 변경된다.")
    void updateOnlyPosition() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        DashboardWidget widget = newWidget(dashboard, "t", Map.of("c", "v0"), Map.of("x", 0));
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardWidgetRepository.findByDashboardWidgetIdAndDashboard_DashboardId(WIDGET_ID, DASHBOARD_ID))
                .thenReturn(Optional.of(widget));

        Map<String, Object> newPosition = Map.of("x", 6, "y", 0, "w", 6, "h", 4);
        dashboardWidgetService.update(owner, DASHBOARD_ID, WIDGET_ID,
                new WidgetUpdateRequest(null, null, newPosition));

        assertThat(widget.getTitle()).isEqualTo("t");
        assertThat(widget.getConfig()).containsEntry("c", "v0");
        assertThat(widget.getPosition()).isEqualTo(newPosition);
    }

    @Test
    @DisplayName("title/config/position 모두 null 이면 INVALID_INPUT 으로 차단된다.")
    void allFieldsNull() {
        assertThatThrownBy(() -> dashboardWidgetService.update(
                owner, DASHBOARD_ID, WIDGET_ID,
                new WidgetUpdateRequest(null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_INPUT);

        verify(dashboardRepository, never()).findById(anyLong());
        verify(dashboardWidgetRepository, never()).findByDashboardWidgetIdAndDashboard_DashboardId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("dashboard 가 존재하지 않으면 DASHBOARD_NOT_FOUND 로 차단된다.")
    void dashboardNotFound() {
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardWidgetService.update(
                owner, DASHBOARD_ID, WIDGET_ID,
                new WidgetUpdateRequest("t", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_NOT_FOUND);

        verify(dashboardWidgetRepository, never()).findByDashboardWidgetIdAndDashboard_DashboardId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("호출자가 dashboard owner 가 아니면 DASHBOARD_ACCESS_DENIED 로 차단된다.")
    void forbiddenWhenNotOwner() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        assertThatThrownBy(() -> dashboardWidgetService.update(
                stranger, DASHBOARD_ID, WIDGET_ID,
                new WidgetUpdateRequest("t", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);

        verify(dashboardWidgetRepository, never()).findByDashboardWidgetIdAndDashboard_DashboardId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("widget 이 없거나 해당 dashboard 소속이 아니면 WIDGET_NOT_FOUND 로 차단된다.")
    void widgetNotFoundOrOtherDashboard() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardWidgetRepository.findByDashboardWidgetIdAndDashboard_DashboardId(WIDGET_ID, DASHBOARD_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardWidgetService.update(
                owner, DASHBOARD_ID, WIDGET_ID,
                new WidgetUpdateRequest("t", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.WIDGET_NOT_FOUND);
    }

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

    private DashboardWidget newWidget(Dashboard dashboard, String title,
                                      Map<String, Object> config, Map<String, Object> position) {
        DashboardWidget widget = DashboardWidget.builder()
                .dashboard(dashboard)
                .widgetType(WidgetType.BAR_CHART)
                .title(title)
                .config(config)
                .position(position)
                .build();
        ReflectionTestUtils.setField(widget, "dashboardWidgetId", WIDGET_ID);
        return widget;
    }
}

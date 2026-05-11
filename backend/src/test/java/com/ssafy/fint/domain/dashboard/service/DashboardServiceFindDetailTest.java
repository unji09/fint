package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.DashboardDetailResponse;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardQuery;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import com.ssafy.fint.domain.dashboard.entity.WidgetType;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardWidgetRepository;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.DashboardErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceFindDetailTest {

    private static final long TENANT_ID = 1L;
    private static final long OWNER_USER_ID = 5L;
    private static final long STRANGER_USER_ID = 77L;
    private static final long DASHBOARD_ID = 10L;

    @Mock private DashboardRepository dashboardRepository;
    @Mock private DashboardWidgetRepository dashboardWidgetRepository;

    private DashboardService dashboardService;

    private final CustomUserDetails owner =
            new CustomUserDetails(OWNER_USER_ID, TENANT_ID, "MEMBER");
    private final CustomUserDetails stranger =
            new CustomUserDetails(STRANGER_USER_ID, TENANT_ID, "MEMBER");

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                null, dashboardRepository, null, dashboardWidgetRepository, null
        );
    }

    @Test
    @DisplayName("대시보드 상세 조회 시 위젯 목록과 쿼리 정보가 함께 반환된다.")
    void findDetailWithWidgets() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID, "내 대시보드");
        DashboardQuery query = newQuery(1L, dashboard, "매출 추이 보여줘",
                Map.of("labels", List.of("W1", "W2"), "values", List.of(100, 200)));
        DashboardWidget widgetWithQuery = newWidget(1L, dashboard, query,
                WidgetType.BAR_CHART, "주간 매출", Map.of("x", 0, "y", 0, "w", 6, "h", 4));
        DashboardWidget widgetWithoutQuery = newWidget(2L, dashboard, null,
                WidgetType.PIE, "세그먼트 비중", Map.of("x", 6, "y", 0, "w", 3, "h", 4));

        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardWidgetRepository.findByDashboard(dashboard))
                .thenReturn(List.of(widgetWithQuery, widgetWithoutQuery));

        DashboardDetailResponse result = dashboardService.findDetail(owner, DASHBOARD_ID);

        assertThat(result.dashboardId()).isEqualTo(DASHBOARD_ID);
        assertThat(result.title()).isEqualTo("내 대시보드");
        assertThat(result.widgets()).hasSize(2);

        var w1 = result.widgets().get(0);
        assertThat(w1.widgetId()).isEqualTo(1L);
        assertThat(w1.queryId()).isEqualTo(1L);
        assertThat(w1.inputText()).isEqualTo("매출 추이 보여줘");
        assertThat(w1.result()).containsKey("labels");

        var w2 = result.widgets().get(1);
        assertThat(w2.widgetId()).isEqualTo(2L);
        assertThat(w2.queryId()).isNull();
        assertThat(w2.inputText()).isNull();
        assertThat(w2.result()).isNull();
    }

    @Test
    @DisplayName("위젯이 없는 대시보드는 빈 위젯 리스트로 반환된다.")
    void findDetailEmptyWidgets() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID, "빈 대시보드");

        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardWidgetRepository.findByDashboard(dashboard)).thenReturn(List.of());

        DashboardDetailResponse result = dashboardService.findDetail(owner, DASHBOARD_ID);

        assertThat(result.widgets()).isEmpty();
    }

    @Test
    @DisplayName("상세 조회 시 lastAccessedAt 이 갱신된다.")
    void findDetailUpdatesLastAccessedAt() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID, "대시보드");

        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardWidgetRepository.findByDashboard(dashboard)).thenReturn(List.of());

        dashboardService.findDetail(owner, DASHBOARD_ID);

        assertThat(dashboard.getLastAccessedAt()).isNotNull();
    }

    @Test
    @DisplayName("존재하지 않는 대시보드이면 DASHBOARD_NOT_FOUND 로 차단된다.")
    void dashboardNotFound() {
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.findDetail(owner, DASHBOARD_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_NOT_FOUND);
    }

    @Test
    @DisplayName("소유자가 아니면 DASHBOARD_ACCESS_DENIED 로 차단된다.")
    void forbiddenWhenNotOwner() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID, "대시보드");
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        assertThatThrownBy(() -> dashboardService.findDetail(stranger, DASHBOARD_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);
    }

    private Dashboard newDashboard(long ownerUserId, String title) {
        User user = User.builder()
                .tenant(newTenant())
                .role(UserRole.MEMBER)
                .name("owner")
                .passwordHash("x")
                .build();
        ReflectionTestUtils.setField(user, "userId", ownerUserId);
        Dashboard dashboard = Dashboard.builder()
                .owner(user)
                .title(title)
                .build();
        ReflectionTestUtils.setField(dashboard, "dashboardId", DASHBOARD_ID);
        return dashboard;
    }

    private Tenant newTenant() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + TENANT_ID).build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);
        return tenant;
    }

    private DashboardQuery newQuery(long id, Dashboard dashboard, String inputText,
                                     Map<String, Object> result) {
        DashboardQuery query = DashboardQuery.builder()
                .dashboard(dashboard)
                .inputText(inputText)
                .result(result)
                .completedAt(OffsetDateTime.now())
                .build();
        ReflectionTestUtils.setField(query, "dashboardQueryId", id);
        return query;
    }

    private DashboardWidget newWidget(long id, Dashboard dashboard, DashboardQuery query,
                                       WidgetType type, String title,
                                       Map<String, Object> position) {
        DashboardWidget widget = DashboardWidget.builder()
                .dashboard(dashboard)
                .dashboardQuery(query)
                .widgetType(type)
                .title(title)
                .config(Map.of())
                .position(position)
                .build();
        ReflectionTestUtils.setField(widget, "dashboardWidgetId", id);
        return widget;
    }
}

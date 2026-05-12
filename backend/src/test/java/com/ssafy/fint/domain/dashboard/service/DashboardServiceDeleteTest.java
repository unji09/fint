package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.entity.Dashboard;
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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceDeleteTest {

    private static final long TENANT_ID = 1L;
    private static final long OWNER_USER_ID = 99L;
    private static final long STRANGER_USER_ID = 77L;
    private static final long DASHBOARD_ID = 5L;

    @Mock private DashboardRepository dashboardRepository;
    @Mock private DashboardWidgetRepository dashboardWidgetRepository;
    @Mock private DashboardQueryRepository dashboardQueryRepository;

    @InjectMocks private DashboardService dashboardService;

    private final CustomUserDetails owner =
            new CustomUserDetails(OWNER_USER_ID, TENANT_ID, "MEMBER");
    private final CustomUserDetails stranger =
            new CustomUserDetails(STRANGER_USER_ID, TENANT_ID, "MEMBER");

    @Test
    @DisplayName("소유자가 삭제하면 위젯 → 쿼리 → 대시보드 순서로 삭제된다.")
    void deleteSuccess() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        dashboardService.delete(owner, DASHBOARD_ID);

        InOrder order = inOrder(dashboardWidgetRepository, dashboardQueryRepository, dashboardRepository);
        order.verify(dashboardWidgetRepository).deleteByDashboard(dashboard);
        order.verify(dashboardQueryRepository).deleteByDashboard(dashboard);
        order.verify(dashboardRepository).delete(dashboard);
    }

    @Test
    @DisplayName("대시보드가 존재하지 않으면 DASHBOARD_NOT_FOUND 로 차단된다.")
    void dashboardNotFound() {
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.delete(owner, DASHBOARD_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_NOT_FOUND);

        verify(dashboardRepository, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("호출자가 소유자가 아니면 DASHBOARD_ACCESS_DENIED 로 차단된다.")
    void forbiddenWhenNotOwner() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        assertThatThrownBy(() -> dashboardService.delete(stranger, DASHBOARD_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);

        verify(dashboardWidgetRepository, never()).deleteByDashboard(org.mockito.ArgumentMatchers.any());
        verify(dashboardRepository, never()).delete(org.mockito.ArgumentMatchers.any());
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
                .title("테스트 대시보드")
                .build();
        ReflectionTestUtils.setField(dashboard, "dashboardId", DASHBOARD_ID);
        return dashboard;
    }

    private Tenant newTenant() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + TENANT_ID).build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);
        return tenant;
    }
}

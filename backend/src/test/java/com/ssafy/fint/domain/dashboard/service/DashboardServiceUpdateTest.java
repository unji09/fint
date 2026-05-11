package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.DashboardUpdateRequest;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceUpdateTest {

    private static final long TENANT_ID = 1L;
    private static final long OWNER_USER_ID = 99L;
    private static final long STRANGER_USER_ID = 77L;
    private static final long DASHBOARD_ID = 5L;

    @Mock private DashboardRepository dashboardRepository;

    @InjectMocks private DashboardService dashboardService;

    private final CustomUserDetails owner =
            new CustomUserDetails(OWNER_USER_ID, TENANT_ID, "MEMBER");
    private final CustomUserDetails stranger =
            new CustomUserDetails(STRANGER_USER_ID, TENANT_ID, "MEMBER");

    @Test
    @DisplayName("title 전달 시 대시보드 제목이 변경된다.")
    void updateTitle() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID, "원래 제목");
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        dashboardService.update(owner, DASHBOARD_ID, new DashboardUpdateRequest("새 제목", null));

        assertThat(dashboard.getTitle()).isEqualTo("새 제목");
    }

    @Test
    @DisplayName("thumbnailUrl 만 전달 시 썸네일만 변경된다.")
    void updateOnlyThumbnailUrl() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID, "제목");
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        dashboardService.update(owner, DASHBOARD_ID,
                new DashboardUpdateRequest(null, "https://s3.example.com/thumb.png"));

        assertThat(dashboard.getTitle()).isEqualTo("제목");
        assertThat(dashboard.getThumbnailUrl()).isEqualTo("https://s3.example.com/thumb.png");
    }

    @Test
    @DisplayName("title 과 thumbnailUrl 모두 null 이면 INVALID_INPUT 으로 차단된다.")
    void allFieldsNullRejected() {
        assertThatThrownBy(() -> dashboardService.update(
                owner, DASHBOARD_ID, new DashboardUpdateRequest(null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INVALID_INPUT);

        verify(dashboardRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("대시보드가 존재하지 않으면 DASHBOARD_NOT_FOUND 로 차단된다.")
    void dashboardNotFound() {
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.update(
                owner, DASHBOARD_ID, new DashboardUpdateRequest("새 제목", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_NOT_FOUND);
    }

    @Test
    @DisplayName("호출자가 대시보드 소유자가 아니면 DASHBOARD_ACCESS_DENIED 로 차단된다.")
    void forbiddenWhenNotOwner() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID, "제목");
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        assertThatThrownBy(() -> dashboardService.update(
                stranger, DASHBOARD_ID, new DashboardUpdateRequest("새 제목", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);

        assertThat(dashboard.getTitle()).isEqualTo("제목");
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
}

package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.DashboardListResponse;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.BusinessException;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceFindAllTest {

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 5L;

    @Mock private UserRepository userRepository;
    @Mock private DashboardRepository dashboardRepository;

    private DashboardService dashboardService;

    private final CustomUserDetails me =
            new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardService(
                userRepository, dashboardRepository, null, null, null
        );
    }

    @Test
    @DisplayName("본인 대시보드 목록을 lastAccessedAt DESC 순으로 반환한다.")
    void findAll() {
        User user = newUser(USER_ID);
        Dashboard d1 = newDashboard(1L, user, "대시보드1", OffsetDateTime.now().minusDays(1));
        Dashboard d2 = newDashboard(2L, user, "대시보드2", OffsetDateTime.now());

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(dashboardRepository.findTop20ByOwnerOrderByLastAccessedAtDesc(user))
                .thenReturn(List.of(d2, d1));

        List<DashboardListResponse> result = dashboardService.findAll(me);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).dashboardId()).isEqualTo(2L);
        assertThat(result.get(1).dashboardId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("대시보드가 없으면 빈 리스트를 반환한다.")
    void findAllEmpty() {
        User user = newUser(USER_ID);

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(dashboardRepository.findTop20ByOwnerOrderByLastAccessedAtDesc(user))
                .thenReturn(List.of());

        List<DashboardListResponse> result = dashboardService.findAll(me);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 사용자이면 예외가 발생한다.")
    void userNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.findAll(me))
                .isInstanceOf(BusinessException.class);
    }

    private User newUser(long userId) {
        User user = User.builder()
                .tenant(newTenant())
                .role(UserRole.MEMBER)
                .name("tester")
                .passwordHash("x")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private Tenant newTenant() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + TENANT_ID).build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);
        return tenant;
    }

    private Dashboard newDashboard(long id, User owner, String title, OffsetDateTime lastAccessed) {
        Dashboard dashboard = Dashboard.builder()
                .owner(owner)
                .title(title)
                .build();
        ReflectionTestUtils.setField(dashboard, "dashboardId", id);
        ReflectionTestUtils.setField(dashboard, "lastAccessedAt", lastAccessed);
        return dashboard;
    }
}

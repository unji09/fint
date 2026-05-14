package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.QueryHistoryResponse;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardQuery;
import com.ssafy.fint.domain.dashboard.repository.DashboardQueryRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceFindQueryHistoryTest {

    private static final long TENANT_ID = 1L;
    private static final long OWNER_USER_ID = 5L;
    private static final long STRANGER_USER_ID = 77L;
    private static final long DASHBOARD_ID = 10L;

    @Mock private DashboardRepository dashboardRepository;
    @Mock private DashboardQueryRepository dashboardQueryRepository;

    @InjectMocks private DashboardService dashboardService;

    private final CustomUserDetails owner =
            new CustomUserDetails(OWNER_USER_ID, TENANT_ID, "MEMBER");
    private final CustomUserDetails stranger =
            new CustomUserDetails(STRANGER_USER_ID, TENANT_ID, "MEMBER");

    @Test
    @DisplayName("쿼리 내역 조회 시 completedAt 오름차순으로 반환된다.")
    void findQueryHistoryReturnsSortedList() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID, "대시보드");

        OffsetDateTime earlier = OffsetDateTime.now().minusHours(2);
        OffsetDateTime later = OffsetDateTime.now().minusHours(1);

        DashboardQuery query1 = newQuery(1L, dashboard, "매출 추이 보여줘",
                Map.of("labels", List.of("W1", "W2"), "values", List.of(100, 200)), earlier);
        DashboardQuery query2 = newQuery(2L, dashboard, "고객 분포 보여줘",
                Map.of("labels", List.of("A", "B"), "values", List.of(50, 80)), later);

        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardQueryRepository.findByDashboard_DashboardIdOrderByCompletedAtAsc(DASHBOARD_ID))
                .thenReturn(List.of(query1, query2));

        List<QueryHistoryResponse> result = dashboardService.findQueryHistory(owner, DASHBOARD_ID);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).queryId()).isEqualTo(1L);
        assertThat(result.get(0).inputText()).isEqualTo("매출 추이 보여줘");
        assertThat(result.get(0).result()).containsKey("labels");
        assertThat(result.get(0).completedAt()).isEqualTo(earlier);
        assertThat(result.get(1).queryId()).isEqualTo(2L);
        assertThat(result.get(1).inputText()).isEqualTo("고객 분포 보여줘");
        assertThat(result.get(1).completedAt()).isEqualTo(later);
    }

    @Test
    @DisplayName("쿼리가 없으면 빈 리스트가 반환된다.")
    void findQueryHistoryReturnsEmptyList() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID, "대시보드");

        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardQueryRepository.findByDashboard_DashboardIdOrderByCompletedAtAsc(DASHBOARD_ID))
                .thenReturn(List.of());

        List<QueryHistoryResponse> result = dashboardService.findQueryHistory(owner, DASHBOARD_ID);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 대시보드이면 DASHBOARD_NOT_FOUND 로 차단된다.")
    void dashboardNotFound() {
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.findQueryHistory(owner, DASHBOARD_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_NOT_FOUND);
    }

    @Test
    @DisplayName("소유자가 아니면 DASHBOARD_ACCESS_DENIED 로 차단된다.")
    void forbiddenWhenNotOwner() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID, "대시보드");
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        assertThatThrownBy(() -> dashboardService.findQueryHistory(stranger, DASHBOARD_ID))
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
                                     Map<String, Object> result, OffsetDateTime completedAt) {
        DashboardQuery query = DashboardQuery.builder()
                .dashboard(dashboard)
                .inputText(inputText)
                .result(result)
                .completedAt(completedAt)
                .build();
        ReflectionTestUtils.setField(query, "dashboardQueryId", id);
        return query;
    }
}

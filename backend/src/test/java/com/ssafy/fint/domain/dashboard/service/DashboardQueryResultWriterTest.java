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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * COMPLETED payload 를 받아 widget/query INSERT 하는 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class DashboardQueryResultWriterTest {

    private static final long TENANT_ID = 1L;
    private static final long DASHBOARD_ID = 5L;
    private static final long NEW_QUERY_ID = 10L;
    private static final long NEW_WIDGET_ID = 20L;
    private static final String INPUT_TEXT = "주간 매출 추이 어때?";

    @Mock private DashboardRepository dashboardRepository;
    @Mock private DashboardQueryRepository dashboardQueryRepository;
    @Mock private DashboardWidgetRepository dashboardWidgetRepository;

    @InjectMocks
    private DashboardQueryResultWriter writer;

    @Test
    @DisplayName("COMPLETED payload 의 result 를 widget/query 로 INSERT 하고 ID 를 반환한다.")
    void persistInsertsQueryAndWidget() {
        Dashboard dashboard = newDashboard();
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardQueryRepository.save(any(DashboardQuery.class))).thenAnswer(inv -> {
            DashboardQuery q = inv.getArgument(0);
            ReflectionTestUtils.setField(q, "dashboardQueryId", NEW_QUERY_ID);
            return q;
        });
        when(dashboardWidgetRepository.save(any(DashboardWidget.class))).thenAnswer(inv -> {
            DashboardWidget w = inv.getArgument(0);
            ReflectionTestUtils.setField(w, "dashboardWidgetId", NEW_WIDGET_ID);
            return w;
        });

        Map<String, Object> result = Map.of(
                "widget_type", "CHART",
                "title", "주간 매출 추이",
                "config", Map.of("colors", "#A8BFA9"),
                "data", Map.of("labels", "W1"),
                "insight_text", "최근 3주간 매출이 60% 증가했습니다.",
                "source_query", "SELECT week, SUM(amount) FROM deals"
        );

        DashboardQueryResultWriter.InsertedIds ids = writer.persist(TENANT_ID, DASHBOARD_ID, INPUT_TEXT, result);

        assertThat(ids.widgetId()).isEqualTo(NEW_WIDGET_ID);
        assertThat(ids.queryId()).isEqualTo(NEW_QUERY_ID);

        ArgumentCaptor<DashboardQuery> queryCaptor = ArgumentCaptor.forClass(DashboardQuery.class);
        verify(dashboardQueryRepository).save(queryCaptor.capture());
        DashboardQuery savedQuery = queryCaptor.getValue();
        assertThat(savedQuery.getDashboard()).isSameAs(dashboard);
        assertThat(savedQuery.getInputText()).isEqualTo(INPUT_TEXT);
        assertThat(savedQuery.getResult()).isEqualTo(result);
        assertThat(savedQuery.getCompletedAt()).isNotNull();

        ArgumentCaptor<DashboardWidget> widgetCaptor = ArgumentCaptor.forClass(DashboardWidget.class);
        verify(dashboardWidgetRepository).save(widgetCaptor.capture());
        DashboardWidget savedWidget = widgetCaptor.getValue();
        assertThat(savedWidget.getDashboard()).isSameAs(dashboard);
        assertThat(savedWidget.getDashboardQuery()).isSameAs(savedQuery);
        assertThat(savedWidget.getWidgetType()).isEqualTo(WidgetType.CHART);
        assertThat(savedWidget.getTitle()).isEqualTo("주간 매출 추이");
        assertThat(savedWidget.getConfig()).containsEntry("colors", "#A8BFA9");
        assertThat(savedWidget.getPosition()).isNull();
        assertThat(savedWidget.getSourceQuery()).isEqualTo("SELECT week, SUM(amount) FROM deals");
    }

    @Test
    @DisplayName("dashboard 가 존재하지 않으면 DASHBOARD_NOT_FOUND 로 차단되고 INSERT 되지 않는다.")
    void notFoundWhenDashboardMissing() {
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> writer.persist(TENANT_ID, DASHBOARD_ID, INPUT_TEXT, Map.of("widget_type", "CHART")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_NOT_FOUND);

        verify(dashboardQueryRepository, never()).save(any());
        verify(dashboardWidgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("result 의 widget_type 이 유효하지 않은 enum 값이면 IllegalArgumentException 이 발생한다.")
    void invalidWidgetTypeThrows() {
        Dashboard dashboard = newDashboard();
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardQueryRepository.save(any(DashboardQuery.class))).thenAnswer(inv -> {
            DashboardQuery q = inv.getArgument(0);
            ReflectionTestUtils.setField(q, "dashboardQueryId", NEW_QUERY_ID);
            return q;
        });

        Map<String, Object> result = Map.of(
                "widget_type", "INVALID_TYPE",
                "title", "test",
                "config", Map.of()
        );

        assertThatThrownBy(() -> writer.persist(TENANT_ID, DASHBOARD_ID, INPUT_TEXT, result))
                .isInstanceOf(IllegalArgumentException.class);

        verify(dashboardWidgetRepository, never()).save(any());
    }

    @Test
    @DisplayName("result 에 config 가 Map 이 아닌 값이면 config 는 null 로 저장된다.")
    void nonMapConfigSavedAsNull() {
        Dashboard dashboard = newDashboard();
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardQueryRepository.save(any(DashboardQuery.class))).thenAnswer(inv -> {
            DashboardQuery q = inv.getArgument(0);
            ReflectionTestUtils.setField(q, "dashboardQueryId", NEW_QUERY_ID);
            return q;
        });
        when(dashboardWidgetRepository.save(any(DashboardWidget.class))).thenAnswer(inv -> {
            DashboardWidget w = inv.getArgument(0);
            ReflectionTestUtils.setField(w, "dashboardWidgetId", NEW_WIDGET_ID);
            return w;
        });

        Map<String, Object> result = Map.of(
                "widget_type", "TABLE",
                "title", "test",
                "config", "not-a-map",
                "source_query", "SELECT 1"
        );

        DashboardQueryResultWriter.InsertedIds ids = writer.persist(TENANT_ID, DASHBOARD_ID, INPUT_TEXT, result);

        assertThat(ids.widgetId()).isEqualTo(NEW_WIDGET_ID);

        ArgumentCaptor<DashboardWidget> captor = ArgumentCaptor.forClass(DashboardWidget.class);
        verify(dashboardWidgetRepository).save(captor.capture());
        assertThat(captor.getValue().getConfig()).isNull();
    }

    @Test
    @DisplayName("tenantId 불일치 → DASHBOARD_ACCESS_DENIED 로 차단되고 INSERT 되지 않는다.")
    void accessDeniedWhenTenantMismatch() {
        Dashboard dashboard = newDashboard();
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        long wrongTenantId = 999L;
        assertThatThrownBy(() -> writer.persist(wrongTenantId, DASHBOARD_ID, INPUT_TEXT, Map.of("widget_type", "CHART")))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);

        verify(dashboardQueryRepository, never()).save(any());
        verify(dashboardWidgetRepository, never()).save(any());
    }

    private Dashboard newDashboard() {
        User owner = User.builder()
                .tenant(newTenant())
                .role(UserRole.MEMBER)
                .name("owner")
                .passwordHash("x")
                .build();
        ReflectionTestUtils.setField(owner, "userId", 99L);
        Dashboard dashboard = Dashboard.builder()
                .owner(owner)
                .title("내 대시보드")
                .build();
        ReflectionTestUtils.setField(dashboard, "dashboardId", DASHBOARD_ID);
        return dashboard;
    }

    private Tenant newTenant() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C1").build();
        ReflectionTestUtils.setField(tenant, "tenantId", 1L);
        return tenant;
    }
}

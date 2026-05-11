package com.ssafy.fint.domain.dashboard.repository;

import com.ssafy.fint.config.TestcontainersConfig;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import com.ssafy.fint.domain.dashboard.entity.WidgetType;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DashboardWidgetRepository.findByDashboardWidgetIdAndDashboard_DashboardId 검증.
 * widget 이 다른 대시보드 소속인 경우 조회되지 않아야 한다 (위젯 수정 시 권한 분리 핵심).
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class DashboardWidgetRepositoryTest {

    @Autowired private DashboardWidgetRepository dashboardWidgetRepository;
    @Autowired private EntityManager em;

    @Test
    @DisplayName("widgetId + dashboardId 가 일치하면 조회된다.")
    void findsWhenWidgetBelongsToDashboard() {
        Dashboard dashboard = persistDashboard();
        DashboardWidget widget = persistWidget(dashboard);
        em.flush();
        em.clear();

        Optional<DashboardWidget> result = dashboardWidgetRepository
                .findByDashboardWidgetIdAndDashboard_DashboardId(
                        widget.getDashboardWidgetId(), dashboard.getDashboardId());

        assertThat(result).isPresent();
        assertThat(result.get().getDashboardWidgetId()).isEqualTo(widget.getDashboardWidgetId());
    }

    @Test
    @DisplayName("widget 이 다른 dashboard 소속이면 조회되지 않는다.")
    void rejectsWhenWidgetBelongsToOtherDashboard() {
        Dashboard dashboardA = persistDashboard();
        Dashboard dashboardB = persistDashboard();
        DashboardWidget widget = persistWidget(dashboardA);
        em.flush();
        em.clear();

        Optional<DashboardWidget> result = dashboardWidgetRepository
                .findByDashboardWidgetIdAndDashboard_DashboardId(
                        widget.getDashboardWidgetId(), dashboardB.getDashboardId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 widgetId 면 조회되지 않는다.")
    void emptyWhenWidgetIdMissing() {
        Dashboard dashboard = persistDashboard();
        em.flush();

        Optional<DashboardWidget> result = dashboardWidgetRepository
                .findByDashboardWidgetIdAndDashboard_DashboardId(99_999L, dashboard.getDashboardId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findAllByDashboard_DashboardId 로 해당 대시보드 소속 위젯만 조회된다.")
    void findsAllWidgetsByDashboardId() {
        Dashboard dashboardA = persistDashboard();
        Dashboard dashboardB = persistDashboard();
        DashboardWidget w1 = persistWidget(dashboardA);
        DashboardWidget w2 = persistWidget(dashboardA);
        persistWidget(dashboardB);
        em.flush();
        em.clear();

        List<DashboardWidget> result = dashboardWidgetRepository
                .findAllByDashboard_DashboardId(dashboardA.getDashboardId());

        assertThat(result).hasSize(2);
        assertThat(result).extracting(DashboardWidget::getDashboardWidgetId)
                .containsExactlyInAnyOrder(w1.getDashboardWidgetId(), w2.getDashboardWidgetId());
    }

    @Test
    @DisplayName("위젯이 없는 대시보드에 findAllByDashboard_DashboardId 호출 시 빈 리스트 반환.")
    void returnsEmptyWhenDashboardHasNoWidgets() {
        Dashboard dashboard = persistDashboard();
        em.flush();
        em.clear();

        List<DashboardWidget> result = dashboardWidgetRepository
                .findAllByDashboard_DashboardId(dashboard.getDashboardId());

        assertThat(result).isEmpty();
    }

    private Dashboard persistDashboard() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C-" + System.nanoTime()).build();
        em.persist(tenant);
        User user = User.builder()
                .tenant(tenant)
                .role(UserRole.MEMBER)
                .name("owner")
                .passwordHash("hash")
                .build();
        em.persist(user);
        Dashboard dashboard = Dashboard.builder()
                .owner(user)
                .title("내 대시보드")
                .build();
        em.persist(dashboard);
        return dashboard;
    }

    private DashboardWidget persistWidget(Dashboard dashboard) {
        DashboardWidget widget = DashboardWidget.builder()
                .dashboard(dashboard)
                .widgetType(WidgetType.BAR_CHART)
                .title("매출 위젯")
                .config(Map.of("k", "v"))
                .position(Map.of("x", 0, "y", 0, "w", 6, "h", 4))
                .build();
        em.persist(widget);
        return widget;
    }
}

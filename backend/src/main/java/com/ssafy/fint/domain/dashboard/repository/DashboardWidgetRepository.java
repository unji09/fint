package com.ssafy.fint.domain.dashboard.repository;

import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DashboardWidgetRepository extends JpaRepository<DashboardWidget, Long> {

    Optional<DashboardWidget> findByDashboardWidgetIdAndDashboard_DashboardId(Long widgetId, Long dashboardId);

    List<DashboardWidget> findByDashboard(Dashboard dashboard);
}

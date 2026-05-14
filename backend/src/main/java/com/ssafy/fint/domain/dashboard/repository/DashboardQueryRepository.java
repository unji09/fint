package com.ssafy.fint.domain.dashboard.repository;

import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardQuery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DashboardQueryRepository extends JpaRepository<DashboardQuery, Long> {

    List<DashboardQuery> findByDashboard_DashboardIdOrderByCompletedAtAsc(Long dashboardId);

    void deleteByDashboard(Dashboard dashboard);
}

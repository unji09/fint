package com.ssafy.fint.domain.dashboard.repository;

import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardWidgetRepository extends JpaRepository<DashboardWidget, Long> {
}

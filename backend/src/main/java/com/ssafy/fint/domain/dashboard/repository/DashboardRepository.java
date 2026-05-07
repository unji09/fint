package com.ssafy.fint.domain.dashboard.repository;

import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardRepository extends JpaRepository<Dashboard, Long> {
}

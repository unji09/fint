package com.ssafy.fint.domain.dashboard.repository;

import com.ssafy.fint.domain.dashboard.entity.DashboardQuery;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DashboardQueryRepository extends JpaRepository<DashboardQuery, Long> {
}

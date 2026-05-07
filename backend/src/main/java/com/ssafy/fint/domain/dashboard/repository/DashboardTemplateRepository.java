package com.ssafy.fint.domain.dashboard.repository;

import com.ssafy.fint.domain.dashboard.entity.DashboardTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DashboardTemplateRepository extends JpaRepository<DashboardTemplate, Long> {

    List<DashboardTemplate> findByDashboardTemplateIdBetween(Long startId, Long endId);
}

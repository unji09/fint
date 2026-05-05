package com.ssafy.fint.domain.tenant.repository;

import com.ssafy.fint.domain.tenant.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    Optional<Team> findByTeamIdAndTenant_TenantId(Long teamId, Long tenantId);
}

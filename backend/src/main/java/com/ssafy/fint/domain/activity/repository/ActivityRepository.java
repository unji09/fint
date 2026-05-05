package com.ssafy.fint.domain.activity.repository;

import com.ssafy.fint.domain.activity.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ActivityRepository
        extends JpaRepository<Activity, Long>, ActivityRepositoryCustom {

    Optional<Activity> findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
            Long activityId, Long userId, Long tenantId);
}

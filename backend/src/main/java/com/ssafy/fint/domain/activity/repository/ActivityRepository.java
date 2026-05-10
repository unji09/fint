package com.ssafy.fint.domain.activity.repository;

import com.ssafy.fint.domain.activity.entity.Activity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ActivityRepository
        extends JpaRepository<Activity, Long>, ActivityRepositoryCustom {

    @EntityGraph(attributePaths = {"deal", "deal.account", "pipelineStage"})
    Optional<Activity> findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
            Long activityId, Long userId, Long tenantId);

    @Query("""
            select count(a) from Activity a
            where a.deal.dealId = :dealId
              and a.type = com.ssafy.fint.domain.activity.entity.ActivityType.MEETING
            """)
    long countMeetingsByDealId(@Param("dealId") Long dealId);
}

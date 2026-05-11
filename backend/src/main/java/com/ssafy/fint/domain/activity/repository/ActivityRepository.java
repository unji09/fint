package com.ssafy.fint.domain.activity.repository;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface ActivityRepository
        extends JpaRepository<Activity, Long>, ActivityRepositoryCustom {

    @EntityGraph(attributePaths = {"deal", "deal.account", "pipelineStage"})
    Optional<Activity> findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
            Long activityId, Long userId, Long tenantId);

    /**
     * 고객사에 매핑된 Deal 에 속한 Activity 중 지정 type 의 개수.
     * deal 미매핑 Activity 는 제외 (CRM 도메인 정의상 자연스러움).
     */
    int countByDeal_Account_AccountIdAndType(Long accountId, ActivityType type);

    /**
     * 고객사에 매핑된 Deal 에 속한 MEETING type Activity 중 가장 최근 startAt.
     * 마지막 미팅 시각 산출용.
     */
    @Query("""
            select max(a.startAt) from Activity a
            where a.deal.account.accountId = :accountId
              and a.type = com.ssafy.fint.domain.activity.entity.ActivityType.MEETING
            """)
    Optional<OffsetDateTime> findLastMeetingStartAtByAccountId(@Param("accountId") Long accountId);

    /**
     * 특정 deal 에 속한 MEETING type Activity 의 개수.
     */
    @Query("""
            select count(a) from Activity a
            where a.deal.dealId = :dealId
              and a.type = com.ssafy.fint.domain.activity.entity.ActivityType.MEETING
            """)
    long countMeetingsByDealId(@Param("dealId") Long dealId);
}

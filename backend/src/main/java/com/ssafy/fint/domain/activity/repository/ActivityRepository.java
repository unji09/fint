package com.ssafy.fint.domain.activity.repository;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
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

    /**
     * 딜의 현재 파이프라인 단계를 산출하기 위한 쿼리.
     * startAt <= 현재 시각이고 pipelineStage 가 설정된 활동 중 가장 최근 것을 반환한다.
     */
    @Query("""
            select a from Activity a
            join fetch a.pipelineStage
            where a.deal.dealId = :dealId
              and a.pipelineStage is not null
              and a.startAt <= current_timestamp
            order by a.startAt desc
            limit 1
            """)
    Optional<Activity> findLatestPipelineActivityByDealId(@Param("dealId") Long dealId);

    /**
     * 브리핑 스케줄러 전용 시스템 배치 쿼리 — 전 테넌트 대상.
     * tenant_id 필터 미적용은 의도적: 스케줄러는 모든 테넌트의 미팅을 한 번에 조회하고,
     * 각 Activity에서 tenantId를 추출해 FastAPI 호출 시 사용한다.
     */
    @Query("""
            SELECT a FROM Activity a
            JOIN FETCH a.user u
            JOIN FETCH u.tenant
            JOIN FETCH a.deal d
            JOIN FETCH d.account
            WHERE a.type = :type
              AND a.startAt BETWEEN :from AND :to
              AND a.briefing IS NULL
            """)
    List<Activity> systemFindUpcomingMeetingsWithoutBriefing(
            @Param("type") ActivityType type,
            @Param("from") OffsetDateTime from,
            @Param("to") OffsetDateTime to
    );

    @Query("""
            SELECT a FROM Activity a
            WHERE a.deal.account.accountId = :accountId
              AND a.user.tenant.tenantId = :tenantId
              AND a.type = com.ssafy.fint.domain.activity.entity.ActivityType.MEETING
              AND a.startAt < :before
            ORDER BY a.startAt DESC
            LIMIT 1
            """)
    Optional<Activity> findRecentMeetingsByAccountId(
            @Param("accountId") Long accountId,
            @Param("tenantId") Long tenantId,
            @Param("before") OffsetDateTime before
    );
}

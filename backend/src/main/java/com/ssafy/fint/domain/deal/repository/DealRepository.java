package com.ssafy.fint.domain.deal.repository;

import com.ssafy.fint.domain.deal.entity.Deal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DealRepository extends JpaRepository<Deal, Long> {

    @Query("""
            select distinct d from Deal d
            join d.account a
            join AccountUserAssignment aua on aua.account = a
            join aua.user u
            where d.dealId = :dealId
              and u.tenant.tenantId = :tenantId
              and u.isDeleted = false
            """)
    Optional<Deal> findByIdAndTenantId(@Param("dealId") Long dealId, @Param("tenantId") Long tenantId);

    /**
     * 고객사 단위 Deal 목록.
     * <p>데이터 스코프 정책:
     * <ul>
     *   <li>{@code callerTeamId} 가 not null 이면 {@code d.team.teamId = callerTeamId} 인 deal 만
     *       (deal.team 이 NULL 인 deal 은 자동 제외).</li>
     *   <li>{@code callerTeamId} 가 null 이면 team 필터 없음 (호출자가 팀 미배정 → tenant 전체 fallback).
     *       account 자체의 tenant 격리는 호출 측 권한 검증으로 보장.</li>
     *   <li>{@code mineOnlyUserId} 가 not null 이면 DealContact 에 해당 user 가 등장하는 deal 만 추가 필터.
     *       null 이면 mineOnly 필터 미적용.</li>
     * </ul>
     * Deal 의 soft delete 필터(is_deleted=false)는 BaseSoftDeletableEntity 의 @SQLRestriction 으로 자동 적용.
     * 정렬은 updated_at desc. Pageable 의 size 로 cap (preview 3개 / 전체는 unpaged) 만 활용.
     */
    @Query("""
            select d from Deal d
            where d.account.accountId = :accountId
              and (:callerTeamId is null
                   or (d.team is not null and d.team.teamId = :callerTeamId))
              and (:mineOnlyUserId is null
                   or exists (
                       select 1 from DealContact dc
                       where dc.deal = d and dc.user.userId = :mineOnlyUserId
                   ))
            order by d.updatedAt desc
            """)
    List<Deal> findByAccountAndScope(
            @Param("accountId") Long accountId,
            @Param("callerTeamId") Long callerTeamId,
            @Param("mineOnlyUserId") Long mineOnlyUserId,
            Pageable pageable);

    @Query(value = """
            select d from Deal d
            where exists (
                select 1 from AccountUserAssignment aua
                where aua.account = d.account
                  and aua.user.tenant.tenantId = :tenantId
                  and aua.user.isDeleted = false
            )
              and (:accountId is null or d.account.accountId = :accountId)
              and (:contactId is null
                   or exists (
                       select 1 from DealContact dc
                       where dc.deal = d and dc.contact.contactId = :contactId
                   ))
            """,
            countQuery = """
            select count(d) from Deal d
            where exists (
                select 1 from AccountUserAssignment aua
                where aua.account = d.account
                  and aua.user.tenant.tenantId = :tenantId
                  and aua.user.isDeleted = false
            )
              and (:accountId is null or d.account.accountId = :accountId)
              and (:contactId is null
                   or exists (
                       select 1 from DealContact dc
                       where dc.deal = d and dc.contact.contactId = :contactId
                   ))
            """)
    Page<Deal> findAllByTenant(@Param("tenantId") Long tenantId,
                               @Param("accountId") Long accountId,
                               @Param("contactId") Long contactId,
                               Pageable pageable);

    @Query(value = """
            select d from Deal d
            where d.team.teamId = :teamId
              and (:accountId is null or d.account.accountId = :accountId)
              and (:contactId is null
                   or exists (
                       select 1 from DealContact dc
                       where dc.deal = d and dc.contact.contactId = :contactId
                   ))
            """,
            countQuery = """
            select count(d) from Deal d
            where d.team.teamId = :teamId
              and (:accountId is null or d.account.accountId = :accountId)
              and (:contactId is null
                   or exists (
                       select 1 from DealContact dc
                       where dc.deal = d and dc.contact.contactId = :contactId
                   ))
            """)
    Page<Deal> findAllByTeam(@Param("teamId") Long teamId,
                             @Param("accountId") Long accountId,
                             @Param("contactId") Long contactId,
                             Pageable pageable);
}

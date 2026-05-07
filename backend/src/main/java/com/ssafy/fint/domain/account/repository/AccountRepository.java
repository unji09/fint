package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.Account;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * 같은 tenant 격리 조회 (다른 도메인 검증용).
     */
    @Query("""
            select distinct a from Account a
            join AccountUserAssignment aua on aua.account = a
            join aua.user u
            where a.accountId = :accountId
              and u.tenant.tenantId = :tenantId
              and u.isDeleted = false
            """)
    Optional<Account> findByIdAndTenantId(
            @Param("accountId") Long accountId,
            @Param("tenantId") Long tenantId);

    /**
     * 본인 책임자 + 같은 tenant 격리 조회 (Account 도메인 권한 검증용).
     */
    @Query("""
            select a from Account a
            join AccountUserAssignment aua on aua.account = a
            where a.accountId = :accountId
              and aua.user.userId = :userId
              and aua.user.tenant.tenantId = :tenantId
              and aua.user.isDeleted = false
            """)
    Optional<Account> findByIdAndAssignedUserIdAndTenantId(
            @Param("accountId") Long accountId,
            @Param("userId") Long userId,
            @Param("tenantId") Long tenantId);

    /**
     * 팀내 고객사 검색 (등록 화면 자동완성용).
     * 같은 tenant + 같은 team 의 사원들이 등록한 account 중 name LIKE keyword 매칭.
     * callerTeamId 가 null 이면 같은 tenant 전체로 fallback (관리자/team 미지정 호출자).
     */
    @Query("""
            select distinct a from Account a
            join AccountUserAssignment aua on aua.account = a
            join aua.user u
            where u.tenant.tenantId = :tenantId
              and (:callerTeamId is null or (u.team is not null and u.team.teamId = :callerTeamId))
              and u.isDeleted = false
              and a.name like concat('%', :keyword, '%')
            """)
    List<Account> searchInTeam(
            @Param("keyword") String keyword,
            @Param("callerTeamId") Long callerTeamId,
            @Param("tenantId") Long tenantId,
            Pageable pageable);
}

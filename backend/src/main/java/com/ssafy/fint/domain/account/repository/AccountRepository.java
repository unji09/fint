package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.Account;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

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

    /**
     * 본인 책임자 + 같은 tenant 인 account 페이지 조회.
     * keyword(name LIKE) · industry(equals) 동적 필터.
     */
    @Query(value = """
            select a from Account a
            join AccountUserAssignment aua on aua.account = a
            where aua.user.userId = :userId
              and aua.user.tenant.tenantId = :tenantId
              and aua.user.isDeleted = false
              and (:keyword is null or a.name like concat('%', :keyword, '%'))
              and (:industry is null or a.industry = :industry)
            """,
            countQuery = """
            select count(a) from Account a
            join AccountUserAssignment aua on aua.account = a
            where aua.user.userId = :userId
              and aua.user.tenant.tenantId = :tenantId
              and aua.user.isDeleted = false
              and (:keyword is null or a.name like concat('%', :keyword, '%'))
              and (:industry is null or a.industry = :industry)
            """)
    Page<Account> findMineByFilter(
            @Param("userId") Long userId,
            @Param("tenantId") Long tenantId,
            @Param("keyword") String keyword,
            @Param("industry") String industry,
            Pageable pageable);
}

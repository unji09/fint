package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * 같은 tenant 격리 조회.
     * 책임자(assignment) 중 한 명 이상이 해당 tenant 에 속하면 OK.
     * AiSuggestion·Deal 등 다른 도메인에서 account 검증용.
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
     * 호출자(userId)가 책임자로 매핑된 account 이고 같은 tenant 에 속하면 OK.
     * 미존재 / 타 사용자 / 타 테넌트 모두 empty Optional 로 통일하여 존재 여부 노출 방지.
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
}

package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * 본인 소유 + 같은 tenant 격리 조회.
     * BaseSoftDeletableEntity 의 @SQLRestriction("is_deleted = false") 가 적용되어
     * 이미 삭제된 account 는 자동으로 제외된다.
     */
    Optional<Account> findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
            Long accountId, Long userId, Long tenantId);
    @Query("""
            select a from Account a
            join a.user u
            where a.accountId = :accountId
              and u.tenant.tenantId = :tenantId
              and u.isDeleted = false
            """)
    Optional<Account> findByIdAndTenantId(@Param("accountId") Long accountId, @Param("tenantId") Long tenantId);
}

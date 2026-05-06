package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    @Query("""
            select a from Account a
            join a.user u
            where a.accountId = :accountId
              and u.tenant.tenantId = :tenantId
              and u.isDeleted = false
            """)
    Optional<Account> findByIdAndTenantId(@Param("accountId") Long accountId, @Param("tenantId") Long tenantId);
}

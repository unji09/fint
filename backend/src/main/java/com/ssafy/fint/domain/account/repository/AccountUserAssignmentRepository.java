package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.AccountUserAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AccountUserAssignmentRepository extends JpaRepository<AccountUserAssignment, Long> {

    Optional<AccountUserAssignment> findByAccount_AccountIdAndUser_UserId(Long accountId, Long userId);

    boolean existsByAccount_AccountIdAndUser_UserId(Long accountId, Long userId);

    void deleteByAccount_AccountIdAndUser_UserId(Long accountId, Long userId);

    /**
     * 호출자가 책임자로 매핑된 account 들의 ID 만 조회 (검색 응답의 assignedToMe 매핑용).
     */
    @Query("""
            select aua.account.accountId from AccountUserAssignment aua
            where aua.user.userId = :userId
              and aua.account.accountId in :accountIds
            """)
    List<Long> findAccountIdsByUserIdAndAccountIdIn(
            @Param("userId") Long userId,
            @Param("accountIds") Collection<Long> accountIds);
}

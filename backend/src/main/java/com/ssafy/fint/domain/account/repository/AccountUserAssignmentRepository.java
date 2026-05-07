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

    @Query("""
            select aua.account.accountId from AccountUserAssignment aua
            where aua.user.userId = :userId
              and aua.account.accountId in :accountIds
            """)
    List<Long> findAccountIdsByUserIdAndAccountIdIn(
            @Param("userId") Long userId,
            @Param("accountIds") Collection<Long> accountIds);

    /**
     * account 의 책임자 매핑을 user 까지 fetch join 으로 한 번에 조회 (상세 조회의 assignedUsers 매핑용).
     */
    @Query("""
            select aua from AccountUserAssignment aua
            join fetch aua.user u
            where aua.account.accountId = :accountId
            """)
    List<AccountUserAssignment> findByAccountIdWithUser(@Param("accountId") Long accountId);
}

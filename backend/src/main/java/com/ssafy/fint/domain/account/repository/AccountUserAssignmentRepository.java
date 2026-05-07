package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.AccountUserAssignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountUserAssignmentRepository extends JpaRepository<AccountUserAssignment, Long> {

    /**
     * (account, user) 쌍에 해당하는 assignment 조회.
     */
    Optional<AccountUserAssignment> findByAccount_AccountIdAndUser_UserId(Long accountId, Long userId);

    /**
     * (account, user) 쌍 assignment 존재 여부.
     * case1 등록 흐름의 idempotent 체크용 (이미 책임자면 중복 INSERT 방지).
     */
    boolean existsByAccount_AccountIdAndUser_UserId(Long accountId, Long userId);

    /**
     * (account, user) 쌍 assignment 제거 (책임 해제).
     */
    void deleteByAccount_AccountIdAndUser_UserId(Long accountId, Long userId);
}

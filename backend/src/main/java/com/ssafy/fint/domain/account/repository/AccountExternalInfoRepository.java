package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.AccountExternalInfo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AccountExternalInfoRepository extends JpaRepository<AccountExternalInfo, Long> {

    /**
     * account_id 별 외부 시그널을 occurred_at 내림차순으로 조회.
     * source 가 null 이면 모든 출처(NEWS/DART) 통합 조회, 지정 시 해당 출처만.
     * Pageable 의 size 가 limit 으로 적용된다.
     */
    @Query("SELECT e FROM AccountExternalInfo e " +
           "WHERE e.account.accountId = :accountId " +
           "AND (:source IS NULL OR e.source = :source) " +
           "ORDER BY e.occurredAt DESC")
    List<AccountExternalInfo> findRecentByAccountAndOptionalSource(
            @Param("accountId") Long accountId,
            @Param("source") String source,
            Pageable pageable);
}

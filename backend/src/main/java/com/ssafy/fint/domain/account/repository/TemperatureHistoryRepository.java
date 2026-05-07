package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.TemperatureHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface TemperatureHistoryRepository extends JpaRepository<TemperatureHistory, Long> {

    /**
     * account_id 별 mood 이력을 created_at 내림차순(최신순)으로 조회.
     */
    List<TemperatureHistory> findByAccount_AccountIdOrderByCreatedAtDesc(Long accountId);

    /**
     * 여러 account 의 최신 mood 만 한 쿼리로 조회 (목록 조회의 latestMood 매핑용).
     * 같은 account 에 동일 created_at row 가 두 개면 둘 다 반환되므로
     * 호출 측에서 첫 매핑만 유지하면 안전하다.
     */
    @Query("""
            select th.account.accountId as accountId, th.mood as mood
            from TemperatureHistory th
            where th.account.accountId in :accountIds
              and th.createdAt = (
                    select max(th2.createdAt) from TemperatureHistory th2
                    where th2.account.accountId = th.account.accountId
              )
            """)
    List<LatestMoodProjection> findLatestMoodsByAccountIds(
            @Param("accountIds") Collection<Long> accountIds);
}

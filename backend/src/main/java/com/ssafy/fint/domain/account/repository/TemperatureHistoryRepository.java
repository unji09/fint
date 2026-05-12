package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.TemperatureHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TemperatureHistoryRepository extends JpaRepository<TemperatureHistory, Long> {

    List<TemperatureHistory> findByAccount_AccountIdOrderByCreatedAtDesc(Long accountId);

    /**
     * account 의 최신 mood 1건 조회 (상세 조회의 latestMood 매핑용).
     */
    Optional<TemperatureHistory> findFirstByAccount_AccountIdOrderByCreatedAtDesc(Long accountId);

    Optional<TemperatureHistory> findTopByActivity_ActivityIdOrderByCreatedAtDesc(Long activityId);

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

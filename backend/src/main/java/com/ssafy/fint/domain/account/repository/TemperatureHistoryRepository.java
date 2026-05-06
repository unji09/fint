package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.TemperatureHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TemperatureHistoryRepository extends JpaRepository<TemperatureHistory, Long> {

    /**
     * account_id 별 온도 이력을 created_at 내림차순(최신순)으로 조회.
     */
    List<TemperatureHistory> findByAccount_AccountIdOrderByCreatedAtDesc(Long accountId);
}

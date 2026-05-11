package com.ssafy.fint.domain.mood.repository;

import com.ssafy.fint.domain.account.entity.TemperatureHistory;import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TemperatureHistoryRepository extends JpaRepository<TemperatureHistory, Long> {

    Optional<TemperatureHistory> findTopByActivity_ActivityIdOrderByCreatedAtDesc(Long activityId);

}

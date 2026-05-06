package com.ssafy.fint.domain.activity.repository;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.service.ActivityListFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface ActivityRepositoryCustom {

    Page<Activity> search(Long tenantId, ActivityListFilter filter, Pageable pageable);

    Optional<Activity> findDetail(Long tenantId, Long activityId);

    Page<Activity> searchByDateRange(
            Long userId,
            Long tenantId,
            OffsetDateTime startInclusive,
            OffsetDateTime endExclusive,
            Pageable pageable
    );
}

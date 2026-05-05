package com.ssafy.fint.domain.activity.repository;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.service.ActivityListFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface ActivityRepositoryCustom {

    Page<Activity> search(ActivityListFilter filter, Pageable pageable);

    Optional<Activity> findDetail(Long activityId);
}

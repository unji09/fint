package com.ssafy.fint.domain.activity.service;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final ActivityRepository activityRepository;

    /**
     * 활동 페이지 조회. tenant 격리는 Repository 레이어에서 자동 적용된다.
     */
    public Page<Activity> findAll(ActivityListFilter filter, Pageable pageable) {
        return activityRepository.search(filter, pageable);
    }
}

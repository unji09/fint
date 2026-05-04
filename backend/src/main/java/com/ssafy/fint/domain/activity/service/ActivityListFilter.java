package com.ssafy.fint.domain.activity.service;

import com.ssafy.fint.domain.activity.entity.ActivityType;

/**
 * 활동 목록 조회 필터.
 * 모든 필드 nullable. null 인 항목은 조건에서 제외된다.
 */
public record ActivityListFilter(
        Long accountId,
        Long dealId,
        ActivityType type
) {
}

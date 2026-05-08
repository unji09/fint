package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.domain.account.entity.Mood;

/**
 * account_id 별 최신 mood 조회용 interface projection (목록 조회의 latestMood 매핑).
 */
public interface LatestMoodProjection {
    Long getAccountId();
    Mood getMood();
}

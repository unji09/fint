package com.ssafy.fint.domain.account.dto;

import com.ssafy.fint.domain.account.entity.Mood;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 고객사 상세 조회 응답.
 * meetingCount / lastContactAt 는 "고객사 매핑 Deal 에 속한 Activity 중 type=MEETING" 기준 집계.
 * deals 는 데이터 스코프 정책(팀 있음→팀 deal, 팀 없음→tenant 전체) 적용된 최신 3개 preview.
 * 전체 목록은 GET /accounts/{accountId}/deals 로.
 */
public record AccountDetailResponse(
        Long accountId,
        String name,
        String industry,
        List<AssignedUser> assignedUsers,
        Mood latestMood,
        Integer meetingCount,
        OffsetDateTime lastContactAt,
        List<ContactItem> contacts,
        List<DealItem> deals
) {
    public record AssignedUser(Long userId, String name) {
    }

    public record ContactItem(
            Long contactId,
            String name,
            String title,
            String phone,
            String email
    ) {
    }

    public record DealItem(
            Long dealId,
            String title,
            String stage,
            Integer probability,
            Long amount
    ) {
    }
}

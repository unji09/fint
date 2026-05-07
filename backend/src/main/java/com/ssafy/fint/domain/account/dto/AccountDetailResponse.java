package com.ssafy.fint.domain.account.dto;

import com.ssafy.fint.domain.account.entity.Mood;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 고객사 상세 조회 응답.
 * 본 task 범위에서는 assignedUsers·latestMood 까지 실구현.
 * meetingCount·lastContactAt·relationDepthScore·contacts·deals 는 후속 작업에서 다른 도메인과 합성 예정.
 */
public record AccountDetailResponse(
        Long accountId,
        String name,
        String industry,
        List<AssignedUser> assignedUsers,
        Mood latestMood,
        Integer meetingCount,
        OffsetDateTime lastContactAt,
        Integer relationDepthScore,
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

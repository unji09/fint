package com.ssafy.fint.domain.account.dto;

import com.ssafy.fint.domain.account.entity.Mood;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 고객사 상세 조회 응답.
 * meetingCount / lastContactAt 는 "고객사 매핑 Deal 에 속한 Activity 중 type=MEETING" 기준 집계.
 * 딜 목록은 GET /accounts/{accountId}/deals 로.
 */
public record AccountDetailResponse(
        Long accountId,
        String name,
        String industry,
        String bizNo,
        List<AssignedUser> assignedUsers,
        Mood latestMood,
        Integer meetingCount,
        OffsetDateTime lastContactAt,
        List<ContactItem> contacts
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
}

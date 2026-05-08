package com.ssafy.fint.domain.account.dto;

import com.ssafy.fint.domain.account.entity.Mood;
import com.ssafy.fint.domain.account.entity.TemperatureHistory;

import java.time.OffsetDateTime;

/**
 * 고객 날씨(분위기) 추이 응답.
 * 엔티티의 createdAt 을 명세상 recordedAt 으로 매핑한다.
 */
public record AccountMoodResponse(
        OffsetDateTime recordedAt,
        Mood mood,
        String reason
) {
    public static AccountMoodResponse from(TemperatureHistory history) {
        return new AccountMoodResponse(
                history.getCreatedAt(),
                history.getMood(),
                history.getReason()
        );
    }
}

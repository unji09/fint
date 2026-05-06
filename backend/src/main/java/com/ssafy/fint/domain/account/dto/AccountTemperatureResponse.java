package com.ssafy.fint.domain.account.dto;

import com.ssafy.fint.domain.account.entity.TemperatureHistory;

import java.time.OffsetDateTime;

public record AccountTemperatureResponse(
        OffsetDateTime recordedAt,
        int temperature,
        String reason
) {
    public static AccountTemperatureResponse from(TemperatureHistory history) {
        return new AccountTemperatureResponse(
                history.getCreatedAt(),
                history.getTemperature(),
                history.getReason()
        );
    }
}

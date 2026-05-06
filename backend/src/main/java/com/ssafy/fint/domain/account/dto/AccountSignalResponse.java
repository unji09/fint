package com.ssafy.fint.domain.account.dto;

import com.ssafy.fint.domain.account.entity.AccountExternalInfo;

import java.time.OffsetDateTime;

public record AccountSignalResponse(
        String source,
        String title,
        String content,
        String url,
        OffsetDateTime occurredAt
) {
    public static AccountSignalResponse from(AccountExternalInfo info) {
        return new AccountSignalResponse(
                info.getSource(),
                info.getTitle(),
                info.getContent(),
                info.getUrl(),
                info.getOccurredAt()
        );
    }
}

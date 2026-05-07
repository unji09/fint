package com.ssafy.fint.domain.account.dto;

import com.ssafy.fint.domain.account.entity.Mood;

import java.util.List;

public record AccountListResponse(
        List<Item> content,
        long totalElements
) {
    public record Item(
            Long accountId,
            String name,
            String industry,
            Mood latestMood
    ) {
    }
}

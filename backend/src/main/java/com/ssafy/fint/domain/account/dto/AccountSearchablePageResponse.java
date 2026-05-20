package com.ssafy.fint.domain.account.dto;

import java.util.List;

public record AccountSearchablePageResponse(
        List<AccountSearchableResponse> content,
        boolean hasNext,
        long totalElements
) {}

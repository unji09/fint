package com.ssafy.fint.domain.ai.dto;

import com.ssafy.fint.domain.ai.entity.TriggerType;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record NextActionCreateRequest(
        @NotNull Long accountId,
        @NotNull TriggerType triggerType,
        List<Long> newsArticleIds,
        List<Long> dartDisclosureIds,
        List<Long> meetingIds,
        String context
) {}

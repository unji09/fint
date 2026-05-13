package com.ssafy.fint.domain.ai.client;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.Map;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NextActionAiResponse(
        String title,
        String description,
        String category,
        Integer successProbability,
        Map<String, Object> sources,
        String recommendedScript,
        String risk,
        Long pipelineStageId
) {}

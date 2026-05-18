package com.ssafy.fint.domain.briefing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BriefingResponse(
        @JsonProperty("key_points") List<String> keyPoints,
        @JsonProperty("alerts") List<String> alerts
) {}

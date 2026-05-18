package com.ssafy.fint.domain.account.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CompanyEnrichApiResponse(int status, String code, String message, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Data(boolean matched, CompanyInfo company, List<CompanyInfo> candidates) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record CompanyInfo(
            String corpCode,
            String corpName,
            String bizNo,
            String industryCode,
            String ceoName,
            String address,
            String phone,
            String establishedDate
    ) {}
}

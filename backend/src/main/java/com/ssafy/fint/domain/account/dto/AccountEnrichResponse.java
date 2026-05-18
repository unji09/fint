package com.ssafy.fint.domain.account.dto;

import com.ssafy.fint.domain.account.client.CompanyEnrichApiResponse;

import java.util.List;

public record AccountEnrichResponse(
        boolean matched,
        CompanyItem company,
        List<CompanyItem> candidates
) {
    public record CompanyItem(
            String corpCode,
            String corpName,
            String bizNo,
            String industryCode,
            String ceoName,
            String address,
            String phone,
            String establishedDate
    ) {}

    public static AccountEnrichResponse from(CompanyEnrichApiResponse.Data data) {
        CompanyItem company = null;
        if (data.company() != null) {
            company = toItem(data.company());
        }

        List<CompanyItem> candidates = List.of();
        if (data.candidates() != null) {
            candidates = data.candidates().stream().map(AccountEnrichResponse::toItem).toList();
        }

        return new AccountEnrichResponse(data.matched(), company, candidates);
    }

    private static CompanyItem toItem(CompanyEnrichApiResponse.CompanyInfo info) {
        return new CompanyItem(
                info.corpCode(), info.corpName(), info.bizNo(),
                info.industryCode(), info.ceoName(), info.address(),
                info.phone(), info.establishedDate()
        );
    }
}

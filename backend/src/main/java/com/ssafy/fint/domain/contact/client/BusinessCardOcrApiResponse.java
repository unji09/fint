package com.ssafy.fint.domain.contact.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * FastAPI {@code POST /api/v1/ocr/business-card} 응답 envelope.
 * 성공 응답엔 {@code code} 가 없고, 실패 응답엔 {@code code} 가 채워지며 {@code data} 는 null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record BusinessCardOcrApiResponse(
        int status,
        String code,
        String message,
        Data data
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            String name,
            String company,
            String title,
            String phone,
            String email
    ) {
    }
}

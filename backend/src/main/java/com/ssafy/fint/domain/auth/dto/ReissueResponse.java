package com.ssafy.fint.domain.auth.dto;

public record ReissueResponse(
    String accessToken,
    String tokenType
) {
    public static ReissueResponse of(String accessToken) {
        return new ReissueResponse(accessToken, "Bearer");
    }
}

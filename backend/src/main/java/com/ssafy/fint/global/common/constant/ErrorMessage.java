package com.ssafy.fint.global.common.constant;

/**
 * 도메인 구현 전 임시 보관소.
 * 각 상수는 도메인 구현 시 해당 도메인의 `<Domain>ErrorCode` enum 으로 이관될 예정이다.
 * 공통 에러 메시지는 {@code CommonErrorCode} 로 이관 완료.
 */
public final class ErrorMessage {

    // Auth / Token (추후 AuthErrorCode 로 이관 예정)
    public static final String ACCESS_TOKEN_EXPIRED = "Access token expired";
    public static final String INVALID_ACCESS_TOKEN = "Invalid access token";
    public static final String REFRESH_TOKEN_EXPIRED = "Refresh token expired";
    public static final String INVALID_REFRESH_TOKEN = "Invalid refresh token";
    public static final String UNAUTHORIZED_USER = "Unauthorized user";

    // Tenant (추후 TenantErrorCode 로 이관 예정)
    public static final String TENANT_MISMATCH = "Tenant mismatch";
    public static final String TENANT_NOT_FOUND = "Tenant not found";

    // AI (추후 AiErrorCode 로 이관 예정)
    public static final String AI_SERVICE_UNAVAILABLE = "AI 서비스를 사용할 수 없습니다.";

    private ErrorMessage() {}
}

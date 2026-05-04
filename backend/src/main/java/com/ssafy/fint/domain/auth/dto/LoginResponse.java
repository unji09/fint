package com.ssafy.fint.domain.auth.dto;

import com.ssafy.fint.domain.user.entity.User;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    UserInfo user
) {
    public record UserInfo(
        Long userId,
        String name,
        String empNo,
        String role,
        Long tenantId,
        Long teamId
    ) {}

    public static LoginResponse of(String accessToken, String refreshToken, User user) {
        return new LoginResponse(
            accessToken,
            refreshToken,
            "Bearer",
            new UserInfo(
                user.getUserId(),
                user.getName(),
                user.getEmpNo(),
                user.getRole().name(),
                user.getTenant().getTenantId(),
                user.getTeam() != null ? user.getTeam().getTeamId() : null
            )
        );
    }
}

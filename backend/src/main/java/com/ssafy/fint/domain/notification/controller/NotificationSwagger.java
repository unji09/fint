package com.ssafy.fint.domain.notification.controller;

import com.ssafy.fint.domain.notification.dto.NotificationListResponse;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Notification", description = "사용자 알림 API")
public interface NotificationSwagger {

    @Operation(
            summary = "알림 목록 조회",
            description = "현재 로그인 사용자별 미확인(읽지 않은) AI 제안을 최신순 10건 반환한다. "
                    + "데이터 소스는 ai_suggestions 테이블이다."
    )
    ApiResponse<NotificationListResponse> findUnread(CustomUserDetails me);
}

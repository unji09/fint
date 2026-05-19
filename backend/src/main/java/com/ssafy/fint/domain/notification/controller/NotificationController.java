package com.ssafy.fint.domain.notification.controller;

import com.ssafy.fint.domain.notification.dto.NotificationListResponse;
import com.ssafy.fint.domain.notification.dto.NotificationReadAllResponse;
import com.ssafy.fint.domain.notification.service.NotificationService;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationSwagger {

    private final NotificationService notificationService;

    @Override
    @GetMapping
    public ApiResponse<NotificationListResponse> findNotifications(
            @AuthenticationPrincipal CustomUserDetails me
    ) {
        return ApiResponse.ok(notificationService.findNotifications(me));
    }

    @Override
    @PatchMapping("/read-all")
    public ApiResponse<NotificationReadAllResponse> markAllAsRead(
            @AuthenticationPrincipal CustomUserDetails me
    ) {
        return ApiResponse.ok(notificationService.markAllAsRead(me));
    }

    @Override
    @PatchMapping("/{notificationId}/read")
    public ApiResponse<Void> markAsRead(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal CustomUserDetails me
    ) {
        notificationService.markAsRead(notificationId, me);
        return ApiResponse.ok();
    }
}

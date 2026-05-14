package com.ssafy.fint.domain.activity.controller;

import com.ssafy.fint.domain.activity.dto.SttCallbackRequest;
import com.ssafy.fint.domain.activity.service.SttCallbackService;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/internal/activities")
@RequiredArgsConstructor
public class SttCallbackController {

    private final SttCallbackService sttCallbackService;

    @Value("${internal.secret}")
    private String internalSecret;

    @PostMapping("/{activityId}/stt/callback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void callback(
        @PathVariable Long activityId,
        @RequestBody SttCallbackRequest request,
        @RequestHeader(value = "X-Internal-Secret", required = false) String secret
    ) {
        if(!internalSecret.equals(secret)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        log.info("[SttCallback] received activityId={}, request={}", activityId, request.accountId());
        sttCallbackService.processCallback(activityId, request);
    }
}

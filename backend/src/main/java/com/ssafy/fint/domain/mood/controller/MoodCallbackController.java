package com.ssafy.fint.domain.mood.controller;

import com.ssafy.fint.domain.mood.dto.MoodCallbackRequest;
import com.ssafy.fint.domain.mood.service.MoodAnalysisService;
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
public class MoodCallbackController {

    private final MoodAnalysisService moodAnalysisService;

    @Value("${internal.secret}")
    private String internalSecret;

    @PostMapping("/{activityId}/ai/mood/callback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void callback(
        @PathVariable Long activityId,
        @RequestBody MoodCallbackRequest request,
        @RequestHeader("X-Internal-Secret") String secret) {
        if(!internalSecret.equals(secret)){
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        log.info("{MoodCallback} received activityId={} accountId={}", activityId, request.accountId());
        moodAnalysisService.processCallback(activityId, request);
    }
}

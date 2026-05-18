package com.ssafy.fint.domain.ai.controller;

import com.ssafy.fint.domain.briefing.BriefingService;
import com.ssafy.fint.domain.briefing.dto.BriefingResponse;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class BriefingController implements BriefingSwagger {

    private final BriefingService briefingService;

    @Override
    @PostMapping("/ai/briefing")
    public ApiResponse<BriefingResponse> generateBriefing(
            @AuthenticationPrincipal CustomUserDetails me,
            @RequestParam Long activityId
    ) {
        return ApiResponse.ok(briefingService.generateForActivity(activityId, me));
    }
}

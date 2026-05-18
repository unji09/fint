package com.ssafy.fint.domain.ai.controller;

import com.ssafy.fint.domain.briefing.dto.BriefingResponse;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "AI Briefing", description = "미팅 브리핑 생성 API")
public interface BriefingSwagger {

    @Operation(
            summary = "미팅 브리핑 생성",
            description = "미팅 활동에 대해 FastAPI AI 서비스를 호출하여 브리핑(key_points + alerts)을 생성하고 Activity에 저장한다. "
                    + "MEETING 유형의 활동이며 연결된 Deal이 있어야 한다."
    )
    ApiResponse<BriefingResponse> generateBriefing(CustomUserDetails me, Long activityId);
}

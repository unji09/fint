package com.ssafy.fint.domain.mood.controller;

import com.ssafy.fint.domain.mood.dto.MoodAnalysisResponse;
import com.ssafy.fint.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Mood", description = "AI 미팅 날씨 분석 API")
public interface MoodAnalysisSwagger {

    @Operation(
        summary = "날씨 분석 결과 조회",
        description = "STT 완료 후 자동 실행된 날씨 분석 결과를 조회한다. "
        + "moodStatus가 PENDING/PROCESSING 이면 분석 중, COMPLETED이면 결과 포함, FAILED이면 실패 상태를 반환한다."
    )
    ApiResponse<MoodAnalysisResponse> getMoodAnalysis(Long activityId);
}

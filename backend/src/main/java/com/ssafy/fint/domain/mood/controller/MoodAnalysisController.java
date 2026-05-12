package com.ssafy.fint.domain.mood.controller;

import com.ssafy.fint.domain.mood.dto.MoodAnalysisResponse;
import com.ssafy.fint.domain.mood.service.MoodAnalysisService;
import com.ssafy.fint.global.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class MoodAnalysisController implements MoodAnalysisSwagger{

    private final MoodAnalysisService moodAnalysisService;

    @Override
    @GetMapping("/{activityId}/ai/mood")
    public ApiResponse<MoodAnalysisResponse> getMoodAnalysis(@PathVariable Long activityId) {
        return ApiResponse.ok(moodAnalysisService.getMoodAnalysis(activityId));
    }
}

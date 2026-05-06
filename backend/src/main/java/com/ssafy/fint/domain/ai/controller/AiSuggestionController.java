package com.ssafy.fint.domain.ai.controller;

import com.ssafy.fint.domain.ai.dto.NextActionDetailResponse;
import com.ssafy.fint.domain.ai.dto.NextActionListResponse;
import com.ssafy.fint.domain.ai.service.AiSuggestionService;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/accounts/{accountId}/ai/next-actions")
@RequiredArgsConstructor
public class AiSuggestionController implements AiSuggestionSwagger {

    private final AiSuggestionService aiSuggestionService;

    @Override
    @GetMapping
    public ApiResponse<List<NextActionListResponse>> list(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable Long accountId
    ) {
        return ApiResponse.ok(aiSuggestionService.findNextActions(me, accountId));
    }

    @Override
    @GetMapping("/{suggestionId}")
    public ApiResponse<NextActionDetailResponse> detail(
        @AuthenticationPrincipal CustomUserDetails me,
        @PathVariable Long accountId,
        @PathVariable Long suggestionId
    ) {
        return ApiResponse.ok(aiSuggestionService.findNextActionDetail(me, accountId, suggestionId));
    }
}

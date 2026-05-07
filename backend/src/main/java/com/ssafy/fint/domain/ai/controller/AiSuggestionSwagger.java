package com.ssafy.fint.domain.ai.controller;

import com.ssafy.fint.domain.ai.dto.NextActionDetailResponse;
import com.ssafy.fint.domain.ai.dto.NextActionListResponse;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "AI Suggestion", description = "고객사 AI 추천 Next Action API")
public interface AiSuggestionSwagger {

    @Operation(
            summary = "고객사 Next Action 목록 조회",
            description = "현재 테넌트 소유의 고객사에 대한 AI 추천 Next Action 목록을 최근 생성순으로 반환한다."
    )
    ApiResponse<List<NextActionListResponse>> list(CustomUserDetails me, Long accountId);

    @Operation(
            summary = "고객사 Next Action 상세 조회",
            description = "특정 Next Action 의 상세 정보(전략/근거 데이터/추천 멘트/주의사항)를 반환한다. "
                    + "근거 데이터(sources)는 NEWS/DART/CRM 으로 구분되는 jsonb 구조이다."
    )
    ApiResponse<NextActionDetailResponse> detail(CustomUserDetails me, Long accountId, Long suggestionId);
}

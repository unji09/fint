package com.ssafy.fint.domain.account.controller;

import com.ssafy.fint.domain.account.dto.AccountMoodResponse;
import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
import com.ssafy.fint.domain.account.dto.AccountSearchableResponse;
import com.ssafy.fint.domain.account.dto.AccountSignalResponse;
import com.ssafy.fint.domain.account.dto.AccountUpdateRequest;
import com.ssafy.fint.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Customer", description = "고객사(Account) 정보 관리 API")
public interface AccountSwagger {

    @Operation(
            summary = "고객사 등록",
            description = "case2(신규): name·industry·bizNo 로 새 고객사 등록 후 호출자를 책임자로 자동 매핑. " +
                    "case1(기존): existingAccountId 만 보내면 기존 account 에 본인 책임자 추가 (idempotent)."
    )
    ApiResponse<AccountRegisterResponse> register(AccountRegisterRequest request);

    @Operation(
            summary = "고객사 수정",
            description = "본인이 책임자로 매핑된 + 같은 tenant 인 account 만 수정. null 인 필드는 변경 안 됨."
    )
    void update(Long accountId, AccountUpdateRequest request);

    @Operation(
            summary = "고객사 책임 해제",
            description = "본인 assignment row 만 제거. account 본체는 유지."
    )
    void delete(Long accountId);

    @Operation(
            summary = "고객사 시그널 조회",
            description = "고객사 외부 시그널(NEWS/DART)을 occurred_at 내림차순으로 조회. size 미지정 시 기본 20."
    )
    ApiResponse<List<AccountSignalResponse>> findSignals(Long accountId, String source, Integer size);

    @Operation(
            summary = "고객 날씨 추이 조회",
            description = "고객사 분위기(mood) 변화 이력을 created_at 내림차순으로 조회. " +
                    "5단계 enum: RAINBOW/SUNNY/CLOUDY/RAINY/THUNDER."
    )
    ApiResponse<List<AccountMoodResponse>> findMoodHistory(Long accountId);

    @Operation(
            summary = "고객사 검색 (팀내)",
            description = "등록 화면 자동완성용. 같은 팀(team_id 일치)의 사원들이 등록한 account 합집합. " +
                    "team 미지정 호출자는 같은 tenant 전체 fallback. size 미지정 시 기본 10."
    )
    ApiResponse<List<AccountSearchableResponse>> searchInTeam(String keyword, Integer size);
}

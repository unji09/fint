package com.ssafy.fint.domain.account.controller;

import com.ssafy.fint.domain.account.dto.AccountMoodResponse;
import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
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
            description = "case2(신규): name·industry·bizNo 로 새 고객사 등록 후 호출자를 책임자로 자동 매핑한다. " +
                    "case1(기존 선택)은 existingAccountId 로 분기하여 assignment 만 추가 — 후속 단계 추가 예정."
    )
    ApiResponse<AccountRegisterResponse> register(AccountRegisterRequest request);

    @Operation(
            summary = "고객사 수정",
            description = "고객사 정보를 부분 수정한다. name·industry·bizNo 는 모두 nullable 이며 null 인 필드는 변경되지 않는다. " +
                    "본인이 책임자로 매핑된 + 같은 tenant 인 account 만 수정 가능. 미존재 / 타 사용자 / 타 테넌트 모두 NOT_FOUND."
    )
    void update(Long accountId, AccountUpdateRequest request);

    @Operation(
            summary = "고객사 책임 해제",
            description = "본인 assignment row 만 제거한다 (account 본체는 유지). " +
                    "다른 책임자 잔존하거나 0명이 되어도 account 자체는 보존된다. " +
                    "미존재 / 타 사용자 / 타 테넌트 모두 NOT_FOUND 로 응답."
    )
    void delete(Long accountId);

    @Operation(
            summary = "고객사 시그널 조회",
            description = "고객사 외부 시그널(NEWS/DART)을 occurred_at 내림차순으로 조회한다. " +
                    "source 미지정 시 모든 출처 통합, 지정 시 해당 출처만. size 미지정 시 기본 20 건. " +
                    "본인 책임 + 같은 tenant 검증 후 조회. 미존재 등은 NOT_FOUND."
    )
    ApiResponse<List<AccountSignalResponse>> findSignals(Long accountId, String source, Integer size);

    @Operation(
            summary = "고객 날씨 추이 조회",
            description = "고객사 분위기(mood) 변화 이력을 created_at 내림차순으로 조회한다. " +
                    "5단계 enum: RAINBOW(무지개)·SUNNY(맑음)·CLOUDY(흐림)·RAINY(비)·THUNDER(천둥). " +
                    "본인 책임 + 같은 tenant 검증 후 조회. 미존재 등은 NOT_FOUND."
    )
    ApiResponse<List<AccountMoodResponse>> findMoodHistory(Long accountId);
}

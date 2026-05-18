package com.ssafy.fint.domain.account.controller;

import com.ssafy.fint.domain.account.dto.AccountDealsResponse;
import com.ssafy.fint.domain.account.dto.AccountDetailResponse;
import com.ssafy.fint.domain.account.dto.AccountEnrichResponse;
import com.ssafy.fint.domain.account.dto.AccountListResponse;
import com.ssafy.fint.domain.account.dto.AccountMoodResponse;
import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
import com.ssafy.fint.domain.account.dto.AccountSearchableResponse;
import com.ssafy.fint.domain.account.dto.AccountSignalResponse;
import com.ssafy.fint.domain.account.dto.AccountUpdateRequest;
import com.ssafy.fint.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

import java.util.List;

@Tag(name = "Customer", description = "고객사(Account) 정보 관리 API")
public interface AccountSwagger {

    @Operation(summary = "고객사 등록",
            description = "case2(신규) 또는 case1(existingAccountId 로 기존 책임자 등록).")
    ApiResponse<AccountRegisterResponse> register(AccountRegisterRequest request);

    @Operation(summary = "고객사 수정",
            description = "본인 책임자 + 같은 tenant 인 account 만 수정. null 필드는 변경 안 됨.")
    void update(Long accountId, AccountUpdateRequest request);

    @Operation(summary = "고객사 책임 해제",
            description = "본인 assignment row 만 제거. account 본체는 유지.")
    void delete(Long accountId);

    @Operation(summary = "고객사 목록 조회",
            description = "본인 책임 account 목록. keyword·industry 필터 + 페이지네이션 + 각 row 최신 mood 포함.")
    ApiResponse<AccountListResponse> findMine(String keyword, String industry, Pageable pageable);

    @Operation(summary = "고객사 검색 (팀내)",
            description = "등록 화면 자동완성용. 같은 팀 사원들 등록 account. team 미지정 호출자는 tenant 전체 fallback.")
    ApiResponse<List<AccountSearchableResponse>> searchInTeam(String keyword, Integer size);

    @Operation(summary = "고객사 상세 조회",
            description = "본인 책임자 + 같은 tenant 검증 후 기본 정보 + assignedUsers + latestMood + 미팅 집계 + " +
                    "contacts + deals 합성 응답. deals 는 데이터 스코프 정책(팀 있음→팀 deal, 팀 없음→tenant 전체) " +
                    "적용된 최신 3개 preview. 전체 목록은 GET /accounts/{accountId}/deals 로.")
    ApiResponse<AccountDetailResponse> findDetail(Long accountId);

    @Operation(summary = "고객사별 딜 목록 조회",
            description = "본인 책임 account 검증 후 데이터 스코프(팀 있음→팀 deal, 팀 없음→tenant 전체) 적용. " +
                    "mineOnly=true 면 DealContact 에 본인이 매핑된 deal 만 추가 필터. " +
                    "정렬은 updated_at desc, 페이지네이션 없음(전체 반환).")
    ApiResponse<AccountDealsResponse> findDealsByAccount(Long accountId, boolean mineOnly);

    @Operation(summary = "고객사 시그널 조회",
            description = "외부 시그널(NEWS/DART) occurred_at 내림차순. size 미지정 기본 20.")
    ApiResponse<List<AccountSignalResponse>> findSignals(Long accountId, String source, Integer size);

    @Operation(summary = "고객 날씨 추이 조회",
            description = "mood 변화 이력 created_at 내림차순. enum: RAINBOW/SUNNY/CLOUDY/RAINY/THUNDER.")
    ApiResponse<List<AccountMoodResponse>> findMoodHistory(Long accountId);

    @Operation(summary = "고객사 정보 보강 (DART)",
            description = "DART에서 고객사명으로 사업자번호·업종 자동 조회. "
                    + "1건 매칭 시 자동 반영, 다수 후보 시 candidates 반환.")
    ApiResponse<AccountEnrichResponse> enrich(Long accountId);
}

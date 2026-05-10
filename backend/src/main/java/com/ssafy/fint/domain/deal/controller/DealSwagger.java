package com.ssafy.fint.domain.deal.controller;

import com.ssafy.fint.domain.deal.dto.DealCreateRequest;
import com.ssafy.fint.domain.deal.dto.DealCreateResponse;
import com.ssafy.fint.domain.deal.dto.DealDetailResponse;
import com.ssafy.fint.domain.deal.dto.DealListResponse;
import com.ssafy.fint.domain.deal.dto.DealUpdateRequest;
import com.ssafy.fint.domain.deal.dto.DealUpdateResponse;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

@Tag(name = "Deal", description = "영업건(Deal) 관리 API")
public interface DealSwagger {

    @Operation(
        summary = "딜 등록",
        description = "현재 테넌트의 영업건을 등록한다. accountId 는 필수이며 동일 테넌트 소유인지 검증한다. "
            + "teamId 가 주어지면 동일 테넌트 소유 여부를 검증한다. "
            + "contacts 배열로 담당자를 함께 연결한다. contactId 가 있으면 기존 담당자(딜의 고객사 소속만 허용)를 조회해 연결하고, "
            + "contactId 가 없으면 더미 담당자를 임시 등록 후 연결한다 (담당자 등록 명세 확정 시 정식 입력으로 교체 예정)."
    )
    ApiResponse<DealCreateResponse> create(CustomUserDetails me, DealCreateRequest request);

    @Operation(
        summary = "딜 목록 조회",
        description = "현재 사용자의 권한 범위 내 영업건 목록을 최근 등록순(createdAt desc, dealId desc)으로 조회한다. "
            + "정렬 기준은 서버 고정 — 클라이언트의 sort 쿼리는 무시된다. "
            + "ADMIN 은 같은 테넌트 전체, MANAGER/MEMBER 는 본인 소속 팀(team_id 일치)의 딜만 반환한다. "
    )
    ApiResponse<DealListResponse> findList(CustomUserDetails me, Long accountId, Pageable pageable);

    @Operation(
        summary = "딜 상세 조회",
        description = "현재 테넌트의 영업건 상세 정보를 조회한다. "
            + "연결된 고객사 담당자(contacts) 목록과 해당 딜의 미팅 수(meetingCount, ActivityType=MEETING)를 함께 반환한다."
    )
    ApiResponse<DealDetailResponse> findDetail(CustomUserDetails me, Long dealId);

    @Operation(
        summary = "딜 수정",
        description = "현재 테넌트의 영업건을 부분 수정한다. null 이 아닌 필드만 반영된다. "
            + "pipelineStageId 는 활동 등록 시 단계 자동 추적 용도로 통합된 필드이며, "
            + "본 PATCH 호출에서도 직접 단계 변경 용도로 사용 가능하다."
    )
    ApiResponse<DealUpdateResponse> update(CustomUserDetails me, Long dealId, DealUpdateRequest request);

    @Operation(
        summary = "딜 소프트 삭제",
        description = "현재 테넌트의 영업건을 소프트 삭제한다 (deals.is_deleted = true). "
            + "DELETE /deals/{dealId} 로 라우팅하며, 실제 데이터는 보존된다. "
            + "성공 시 204 No Content 를 반환한다."
    )
    void softDelete(CustomUserDetails me, Long dealId);
}

package com.ssafy.fint.domain.deal.controller;

import com.ssafy.fint.domain.deal.dto.DealCreateRequest;
import com.ssafy.fint.domain.deal.dto.DealCreateResponse;
import com.ssafy.fint.domain.deal.dto.DealDetailResponse;
import com.ssafy.fint.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Deal", description = "영업건(Deal) 관리 API")
public interface DealSwagger {

    @Operation(
        summary = "딜 등록",
        description = "현재 테넌트의 영업건을 등록한다. accountId 는 필수이며 동일 테넌트 소유인지 검증한다. "
            + "teamId 가 주어지면 동일 테넌트 소유 여부를 검증한다. "
            + "contacts 배열로 담당자를 함께 연결한다. contactId 가 있으면 기존 담당자(딜의 고객사 소속만 허용)를 조회해 연결하고, "
            + "contactId 가 없으면 더미 담당자를 임시 등록 후 연결한다 (담당자 등록 명세 확정 시 정식 입력으로 교체 예정)."
    )
    ApiResponse<DealCreateResponse> create(DealCreateRequest request);

    @Operation(
        summary = "딜 상세 조회",
        description = "현재 테넌트의 영업건 상세 정보를 조회한다. 연결된 고객사 담당자(contacts) 목록을 함께 반환한다."
    )
    ApiResponse<DealDetailResponse> findDetail(Long dealId);
}

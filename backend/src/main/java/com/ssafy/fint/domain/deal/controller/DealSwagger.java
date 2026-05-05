package com.ssafy.fint.domain.deal.controller;

import com.ssafy.fint.domain.deal.dto.DealCreateRequest;
import com.ssafy.fint.domain.deal.dto.DealCreateResponse;
import com.ssafy.fint.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Deal", description = "영업건(Deal) 관리 API")
public interface DealSwagger {

    @Operation(
        summary = "딜 등록",
        description = "현재 테넌트의 영업건을 등록한다. accountId 는 필수이며 동일 테넌트 소유인지 검증한다. "
            + "teamId 가 주어지면 동일 테넌트 소유 여부를 검증한다."
    )
    ApiResponse<DealCreateResponse> create(DealCreateRequest request);
}

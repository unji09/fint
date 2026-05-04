package com.ssafy.fint.domain.activity.controller;

import com.ssafy.fint.domain.activity.dto.ActivityCreateRequest;
import com.ssafy.fint.domain.activity.dto.ActivityCreateResponse;
import com.ssafy.fint.domain.activity.dto.ActivityListResponse;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

@Tag(name = "Activity", description = "영업 활동(Activity) 기록 및 관리 API")
public interface ActivitySwagger {

    @Operation(
            summary = "영업 활동 목록 조회",
            description = "현재 테넌트의 활동을 startAt 내림차순으로 페이지 단위로 반환한다. accountId·dealId·type 필터는 AND로 결합되며 nullable이다."
    )
    ApiResponse<ActivityListResponse> list(
            Long accountId,
            Long dealId,
            ActivityType type,
            Pageable pageable
    );

    @Operation(
            summary = "영업 활동 등록",
            description = "현재 테넌트의 활동을 등록한다. dealId·pipelineStageId 가 주어지면 동일 테넌트 소유 여부를 검증한다."
    )
    ApiResponse<ActivityCreateResponse> create(ActivityCreateRequest request);
}

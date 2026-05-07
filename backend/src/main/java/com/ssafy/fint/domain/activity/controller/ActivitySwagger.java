package com.ssafy.fint.domain.activity.controller;

import com.ssafy.fint.domain.activity.dto.ActivityCreateRequest;
import com.ssafy.fint.domain.activity.dto.ActivityCreateResponse;
import com.ssafy.fint.domain.activity.dto.ActivityDetailResponse;
import com.ssafy.fint.domain.activity.dto.ActivityListResponse;
import com.ssafy.fint.domain.activity.dto.ActivityUpdateRequest;
import com.ssafy.fint.domain.activity.dto.ActivityUpdateResponse;
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

    @Operation(
            summary = "영업 활동 상세 조회",
            description = "현재 테넌트의 활동 단건을 STT 전사본·AI 요약·attendees 와 함께 반환한다. "
                    + "dealId 쿼리 파라미터가 주어지면 활동의 dealId 와 일치하는지 검증하고 불일치 시 404 를 반환한다. "
    )
    ApiResponse<ActivityDetailResponse> detail(Long activityId, Long dealId);

    @Operation(
            summary = "영업 활동 삭제",
            description = "본인이 만든 활동 단건을 삭제한다. 성공 시 204 No Content 를 반환하며, "
                    + "같은 테넌트의 다른 사용자가 만든 활동이거나 미존재 ID 인 경우 404 를 반환한다."
    )
    void delete(Long activityId);

    @Operation(
            summary = "영업 활동 부분 수정",
            description = "현재 사용자가 작성한 활동을 부분 수정한다. "
                    + "JSON 키를 생략하면 해당 필드는 변경되지 않고, 명시적 null 로 보내면 해당 필드가 비워진다 "
    )
    ApiResponse<ActivityUpdateResponse> update(Long activityId, ActivityUpdateRequest request);
}

package com.ssafy.fint.domain.activity.controller;

import com.ssafy.fint.domain.activity.dto.*;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.service.ActivityListFilter;
import com.ssafy.fint.domain.activity.service.ActivityService;
import com.ssafy.fint.domain.activity.service.RecordingService;
import com.ssafy.fint.global.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/activities")
@RequiredArgsConstructor
public class ActivityController implements ActivitySwagger {

    private final ActivityService activityService;
    private final RecordingService recordingService;

    @Override
    @GetMapping
    public ApiResponse<ActivityListResponse> list(
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) Long dealId,
            @RequestParam(required = false) ActivityType type,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        ActivityListFilter filter = new ActivityListFilter(accountId, dealId, type);
        return ApiResponse.ok(ActivityListResponse.from(activityService.findAll(filter, pageable)));
    }

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ActivityCreateResponse> create(@Valid @RequestBody ActivityCreateRequest request) {
        return ApiResponse.created(activityService.create(request));
    }

    @Override
    @GetMapping("/{activityId}")
    public ApiResponse<ActivityDetailResponse> detail(
            @PathVariable Long activityId,
            @RequestParam(required = false) Long dealId
    ) {
        return ApiResponse.ok(activityService.findDetail(activityId, dealId));
    }

    @Override
    @PatchMapping("/{activityId}")
    public ApiResponse<ActivityUpdateResponse> update(
            @PathVariable Long activityId,
            @Valid @RequestBody ActivityUpdateRequest request
    ) {
        return ApiResponse.ok(activityService.update(activityId, request));
    }

    @Override
    @DeleteMapping("/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long activityId) {
        activityService.delete(activityId);
    }

    @Override
    @PostMapping("/{activityId}/recording")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<RecordingResponse> recording(
        @PathVariable Long activityId,
        @Valid @RequestBody RecordingRequest request
    ) {
        return ApiResponse.ok(recordingService.create(activityId, request));
    }

    @Override
    @GetMapping("/{activityId}/recordings")
    public ApiResponse<RecordingListResponse> recordings(@PathVariable Long activityId) {
        return ApiResponse.ok(recordingService.list(activityId));
    }

    @Override
    @PatchMapping("/{activityId}/recordings/{recordingId}")
    public ApiResponse<RecordingResponse> updateRecording(
            @PathVariable Long activityId,
            @PathVariable Long recordingId,
            @Valid @RequestBody RecordingUpdateRequest request
    ) {
        return ApiResponse.ok(recordingService.updateTitle(activityId, recordingId, request));
    }

    @Override
    @DeleteMapping("/{activityId}/recordings/{recordingId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecording(
            @PathVariable Long activityId,
            @PathVariable Long recordingId
    ) {
        recordingService.delete(activityId, recordingId);
    }
}

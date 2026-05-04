package com.ssafy.fint.domain.activity.controller;

import com.ssafy.fint.domain.activity.dto.ActivityCreateRequest;
import com.ssafy.fint.domain.activity.dto.ActivityCreateResponse;
import com.ssafy.fint.domain.activity.dto.ActivityListResponse;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.service.ActivityListFilter;
import com.ssafy.fint.domain.activity.service.ActivityService;
import com.ssafy.fint.global.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
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
}

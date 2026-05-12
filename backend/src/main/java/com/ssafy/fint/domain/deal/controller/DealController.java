package com.ssafy.fint.domain.deal.controller;

import com.ssafy.fint.domain.deal.dto.DealCreateRequest;
import com.ssafy.fint.domain.deal.dto.DealCreateResponse;
import com.ssafy.fint.domain.deal.dto.DealDetailResponse;
import com.ssafy.fint.domain.deal.dto.DealListResponse;
import com.ssafy.fint.domain.deal.dto.DealStageResponse;
import com.ssafy.fint.domain.deal.dto.DealUpdateRequest;
import com.ssafy.fint.domain.deal.dto.DealUpdateResponse;
import com.ssafy.fint.domain.deal.service.DealService;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
@RequestMapping("/deals")
@RequiredArgsConstructor
public class DealController implements DealSwagger {

    private static final Sort DEAL_LIST_SORT =
            Sort.by(Sort.Direction.DESC, "createdAt", "dealId");
    private static final int DEAL_LIST_MAX_PAGE_SIZE = 100;

    private final DealService dealService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DealCreateResponse> create(
            @AuthenticationPrincipal CustomUserDetails me,
            @Valid @RequestBody DealCreateRequest request
    ) {
        return ApiResponse.created(dealService.create(me, request));
    }

    @Override
    @GetMapping
    public ApiResponse<DealListResponse> findList(
            @AuthenticationPrincipal CustomUserDetails me,
            @RequestParam(required = false) Long accountId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        int cappedSize = Math.min(pageable.getPageSize(), DEAL_LIST_MAX_PAGE_SIZE);
        Pageable sorted = PageRequest.of(pageable.getPageNumber(), cappedSize, DEAL_LIST_SORT);
        return ApiResponse.ok(dealService.findList(me, accountId, sorted));
    }

    @Override
    @GetMapping("/{dealId}/stage")
    public ApiResponse<DealStageResponse> findCurrentStage(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable Long dealId
    ) {
        return ApiResponse.ok(dealService.findCurrentStage(me, dealId));
    }

    @Override
    @GetMapping("/{dealId}")
    public ApiResponse<DealDetailResponse> findDetail(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable Long dealId
    ) {
        return ApiResponse.ok(dealService.findDetail(me, dealId));
    }

    @Override
    @PatchMapping("/{dealId}")
    public ApiResponse<DealUpdateResponse> update(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable Long dealId,
            @RequestBody DealUpdateRequest request
    ) {
        return ApiResponse.ok(dealService.update(me, dealId, request));
    }

    @Override
    @DeleteMapping("/{dealId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDelete(
            @AuthenticationPrincipal CustomUserDetails me,
            @PathVariable Long dealId
    ) {
        dealService.softDelete(me, dealId);
    }
}

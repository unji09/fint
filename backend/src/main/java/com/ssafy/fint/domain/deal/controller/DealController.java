package com.ssafy.fint.domain.deal.controller;

import com.ssafy.fint.domain.deal.dto.DealCreateRequest;
import com.ssafy.fint.domain.deal.dto.DealCreateResponse;
import com.ssafy.fint.domain.deal.dto.DealDetailResponse;
import com.ssafy.fint.domain.deal.dto.DealUpdateRequest;
import com.ssafy.fint.domain.deal.dto.DealUpdateResponse;
import com.ssafy.fint.domain.deal.service.DealService;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/deals")
@RequiredArgsConstructor
public class DealController implements DealSwagger {

    private final DealService dealService;

    @Override
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DealCreateResponse> create(@Valid @RequestBody DealCreateRequest request) {
        return ApiResponse.created(dealService.create(request));
    }

    @Override
    @GetMapping("/{dealId}")
    public ApiResponse<DealDetailResponse> findDetail(@PathVariable Long dealId) {
        return ApiResponse.ok(dealService.findDetail(dealId));
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
    @PatchMapping("/{dealId}/delete")
    public ApiResponse<Void> softDelete(@PathVariable Long dealId) {
        dealService.softDelete(dealId);
        return ApiResponse.ok();
    }
}

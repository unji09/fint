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
import com.ssafy.fint.domain.account.service.AccountService;
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

import java.util.List;

@RestController
@RequestMapping("/accounts")
@RequiredArgsConstructor
public class AccountController implements AccountSwagger {

    private final AccountService accountService;

    @Override
    @PostMapping
    public ApiResponse<AccountRegisterResponse> register(
            @Valid @RequestBody AccountRegisterRequest request
    ) {
        return ApiResponse.created(accountService.register(request));
    }

    @Override
    @PatchMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(
            @PathVariable Long accountId,
            @Valid @RequestBody AccountUpdateRequest request
    ) {
        accountService.update(accountId, request);
    }

    @Override
    @DeleteMapping("/{accountId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long accountId) {
        accountService.delete(accountId);
    }

    @Override
    @GetMapping
    public ApiResponse<AccountListResponse> findMine(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String industry,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(accountService.findMine(keyword, industry, pageable));
    }

    @Override
    @GetMapping("/searchable")
    public ApiResponse<List<AccountSearchableResponse>> searchInTeam(
            @RequestParam String keyword,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(accountService.searchInTeam(keyword, size));
    }

    @Override
    @GetMapping("/{accountId}")
    public ApiResponse<AccountDetailResponse> findDetail(@PathVariable Long accountId) {
        return ApiResponse.ok(accountService.findDetail(accountId));
    }

    @Override
    @GetMapping("/{accountId}/deals")
    public ApiResponse<AccountDealsResponse> findDealsByAccount(
            @PathVariable Long accountId,
            @RequestParam(required = false, defaultValue = "false") boolean mineOnly
    ) {
        return ApiResponse.ok(accountService.findDealsByAccount(accountId, mineOnly));
    }

    @Override
    @GetMapping("/{accountId}/signals")
    public ApiResponse<List<AccountSignalResponse>> findSignals(
            @PathVariable Long accountId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) Integer size
    ) {
        return ApiResponse.ok(accountService.findSignals(accountId, source, size));
    }

    @Override
    @GetMapping("/{accountId}/mood")
    public ApiResponse<List<AccountMoodResponse>> findMoodHistory(
            @PathVariable Long accountId
    ) {
        return ApiResponse.ok(accountService.findMoodHistory(accountId));
    }

    @Override
    @PostMapping("/{accountId}/enrich")
    public ApiResponse<AccountEnrichResponse> enrich(@PathVariable Long accountId) {
        return ApiResponse.ok(accountService.enrich(accountId));
    }
}

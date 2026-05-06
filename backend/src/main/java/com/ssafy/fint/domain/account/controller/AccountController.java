package com.ssafy.fint.domain.account.controller;

import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
import com.ssafy.fint.domain.account.dto.AccountSignalResponse;
import com.ssafy.fint.domain.account.dto.AccountUpdateRequest;
import com.ssafy.fint.domain.account.service.AccountService;
import com.ssafy.fint.global.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ApiResponse<Void> update(
            @PathVariable Long accountId,
            @Valid @RequestBody AccountUpdateRequest request
    ) {
        accountService.update(accountId, request);
        return ApiResponse.ok();
    }

    @Override
    @DeleteMapping("/{accountId}")
    public ApiResponse<Void> delete(@PathVariable Long accountId) {
        accountService.delete(accountId);
        return ApiResponse.ok();
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
}

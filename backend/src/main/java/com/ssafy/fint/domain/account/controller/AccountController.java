package com.ssafy.fint.domain.account.controller;

import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
import com.ssafy.fint.domain.account.service.AccountService;
import com.ssafy.fint.global.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    @DeleteMapping("/{accountId}")
    public ApiResponse<Void> delete(@PathVariable Long accountId) {
        accountService.delete(accountId);
        return ApiResponse.ok();
    }
}

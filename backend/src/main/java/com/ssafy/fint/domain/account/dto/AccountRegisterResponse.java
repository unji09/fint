package com.ssafy.fint.domain.account.dto;

public record AccountRegisterResponse(
        Long accountId
) {
    public static AccountRegisterResponse of(Long accountId) {
        return new AccountRegisterResponse(accountId);
    }
}

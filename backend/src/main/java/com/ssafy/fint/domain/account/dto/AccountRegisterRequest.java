package com.ssafy.fint.domain.account.dto;

/**
 * 고객사 등록 요청.
 * - case1 (기존 선택): existingAccountId 만 전달. name·industry·bizNo 는 무시된다.
 * - case2 (신규): existingAccountId 없이 name·industry 필수, bizNo 는 선택.
 */
public record AccountRegisterRequest(
        Long existingAccountId,
        String name,
        String industry,
        String bizNo
) {
}

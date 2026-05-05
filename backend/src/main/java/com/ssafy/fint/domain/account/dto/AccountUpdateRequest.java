package com.ssafy.fint.domain.account.dto;

/**
 * 고객사 부분 수정 요청.
 * 모든 필드는 nullable 이며, null 인 필드는 변경되지 않는다.
 */
public record AccountUpdateRequest(
        String name,
        String industry,
        String bizNo
) {
}

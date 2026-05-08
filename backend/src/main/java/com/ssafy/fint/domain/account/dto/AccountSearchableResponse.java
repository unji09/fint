package com.ssafy.fint.domain.account.dto;

/**
 * 고객사 팀내 검색 응답 (등록 화면 자동완성용).
 * assignedToMe 는 본인이 이미 책임자인지 여부 (UI 라벨 분기용).
 */
public record AccountSearchableResponse(
        Long accountId,
        String name,
        String industry,
        String bizNo,
        boolean assignedToMe
) {
}

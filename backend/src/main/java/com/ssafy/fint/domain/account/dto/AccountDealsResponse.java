package com.ssafy.fint.domain.account.dto;

import java.util.List;

/**
 * 고객사별 딜 목록 조회(GET /accounts/{accountId}/deals) 응답.
 * <p>데이터 스코프 정책: 호출자가 팀에 속하면 같은 팀 deal, 미배정이면 같은 tenant 전체.
 * mineOnly=true 면 위 정책에 더해 DealContact 에 호출자가 user 로 등장한 deal 만.
 * 정렬은 updated_at desc, 페이지네이션 없음(전체 반환).
 */
public record AccountDealsResponse(List<DealItem> deals) {

    public record DealItem(
            Long dealId,
            String title,
            String stage,
            Integer probability,
            Long amount
    ) {
    }
}

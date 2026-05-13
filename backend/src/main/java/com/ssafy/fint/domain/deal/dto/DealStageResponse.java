package com.ssafy.fint.domain.deal.dto;

public record DealStageResponse(Long stageId, String stageName) {

    public static final DealStageResponse EMPTY = new DealStageResponse(null, null);
}

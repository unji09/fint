package com.ssafy.fint.domain.deal.dto;

import com.ssafy.fint.domain.deal.entity.PipelineStage;

public record PipelineStageResponse(Long stageId, String stageName) {

    public static PipelineStageResponse from(PipelineStage stage) {
        if (stage == null) {
            return null;
        }
        return new PipelineStageResponse(stage.getPipelineStageId(), stage.getName());
    }
}

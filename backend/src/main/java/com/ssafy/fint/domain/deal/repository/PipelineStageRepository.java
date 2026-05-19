package com.ssafy.fint.domain.deal.repository;

import com.ssafy.fint.domain.deal.entity.PipelineStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PipelineStageRepository extends JpaRepository<PipelineStage, Long> {

    Optional<PipelineStage> findByPipelineStageIdAndTenant_TenantId(Long pipelineStageId, Long tenantId);

    Optional<PipelineStage> findFirstByTenant_TenantIdOrderBySortOrderAsc(Long tenantId);
}

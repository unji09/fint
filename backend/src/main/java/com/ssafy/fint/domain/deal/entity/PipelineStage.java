package com.ssafy.fint.domain.deal.entity;

import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파이프라인 단계(테넌트별 영업 진행 단계 정의).
 */
@Entity
@Table(name = "pipeline_stages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PipelineStage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pipeline_stage_id")
    private Long pipelineStageId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Builder
    private PipelineStage(Tenant tenant, String name, int sortOrder) {
        this.tenant = tenant;
        this.name = name;
        this.sortOrder = sortOrder;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }
}

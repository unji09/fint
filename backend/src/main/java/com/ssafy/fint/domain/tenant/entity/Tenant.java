package com.ssafy.fint.domain.tenant.entity;

import com.ssafy.fint.global.common.entity.BaseSoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 테넌트(서비스 계약 단위 - 회사).
 * 모든 도메인의 최상위 격리 단위.
 */
@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant extends BaseSoftDeletableEntity {

    private static final String DEFAULT_PLAN = "BASIC";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "company_code", nullable = false, length = 20, unique = true)
    private String companyCode;

    @Column(name = "biz_no", length = 20)
    private String bizNo;

    @Column(name = "plan", length = 20)
    private String plan;

    @Builder
    private Tenant(String name, String companyCode, String bizNo, String plan) {
        this.name = name;
        this.companyCode = companyCode;
        this.bizNo = bizNo;
        this.plan = (plan == null) ? DEFAULT_PLAN : plan;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changePlan(String plan) {
        this.plan = plan;
    }
}

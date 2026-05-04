package com.ssafy.fint.domain.tenant.entity;

import com.ssafy.fint.global.common.entity.BaseUpdatableEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant extends BaseUpdatableEntity {
    private static final String DEFAULT_PLAN = "BASIC";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tenant_id")
    private Long tenantId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String companyCode;

    @Column(length = 20)
    private String bizNo;

    @Column(length = 20)
    private String plan;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Builder
    private Tenant(String name, String companyCode, String bizNo, String plan) {
        this.name = name;
        this.companyCode = companyCode;
        this.bizNo = bizNo;
        this.plan = (plan == null) ? DEFAULT_PLAN : plan;
        this.isDeleted = false;
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changePlan(String plan) {
        this.plan = plan;
    }
}

package com.ssafy.fint.domain.tenant.entity;

import com.ssafy.fint.global.common.entity.BaseUpdatableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenants")
@AttributeOverride(name = "id", column = @Column(name = "tenant_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant extends BaseUpdatableEntity {
    private static final String DEFAULT_PLAN = "BASIC";

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 20)
    private String companyCode;

    @Column(length = 20)
    private String bizNo;

    @Column(length = 20)
    private String plan;

    @Column(nullable = false)
    private Boolean isDeleted;
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

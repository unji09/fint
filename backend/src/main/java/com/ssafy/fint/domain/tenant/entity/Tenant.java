package com.ssafy.fint.domain.tenant.entity;

import com.ssafy.fint.global.common.entity.BaseUpdatableEntity;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tenants")
@AttributeOverride(name = "id", column = @Column(name = "tenant_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tenant extends BaseUpdatableEntity {

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
}

package com.ssafy.fint.global.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 모든 JPA 도메인 Entity의 공통 베이스.
 *
 * - tenant_id: 멀티테넌트 격리 필드. 모든 쿼리는 tenant_id 조건을 강제해야 한다.
 * - createdAt / updatedAt: JPA Auditing 으로 자동 갱신 (JpaConfig 에서 @EnableJpaAuditing).
 *
 * 자식 Entity 에서는 @Entity, @Table(name = "...")를 선언하고, @Id는 자식 필드로 관리한다.
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private String tenantId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}

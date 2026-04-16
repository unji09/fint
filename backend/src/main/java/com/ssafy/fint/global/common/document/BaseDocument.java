package com.ssafy.fint.global.common.document;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;

import java.time.LocalDateTime;

/**
 * 모든 MongoDB 도메인 Document의 공통 베이스.
 *
 * - tenant_id: 멀티테넌트 격리 필드. 모든 Document는 tenant_id로 필터링되어야 한다.
 * - createdAt / updatedAt: Mongo Auditing 필요 (MongoConfig 에서 @EnableMongoAuditing).
 *
 * 자식 Document 에서는 @Document(collection = "...")를 선언하고, @Id는 자식 필드로 관리한다.
 */
@Getter
@Setter
public abstract class BaseDocument {

    @Indexed
    private String tenantId;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}

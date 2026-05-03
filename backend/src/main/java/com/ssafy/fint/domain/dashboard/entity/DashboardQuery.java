package com.ssafy.fint.domain.dashboard.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 자연어 쿼리 처리 결과(완료 시점에 INSERT, append-only).
 * 진행 중 상태는 Redis 에서 관리되며 본 테이블에는 완료된 쿼리만 저장된다.
 */
@Entity
@Table(name = "dashboard_queries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DashboardQuery extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dashboard_query_id")
    private Long dashboardQueryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dashboard_id", nullable = false)
    private Dashboard dashboard;

    @Column(name = "input_text", nullable = false, columnDefinition = "TEXT")
    private String inputText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> result;

    @Column(name = "completed_at", nullable = false)
    private OffsetDateTime completedAt;

    @Builder
    private DashboardQuery(
            Dashboard dashboard,
            String inputText,
            Map<String, Object> result,
            OffsetDateTime completedAt
    ) {
        this.dashboard = dashboard;
        this.inputText = inputText;
        this.result = result;
        this.completedAt = completedAt;
    }
}

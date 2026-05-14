package com.ssafy.fint.domain.dashboard.entity;

import com.ssafy.fint.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;

/**
 * 시스템 제공 대시보드 위젯 템플릿.
 * 사용자가 위젯 추가 시 베이스로 사용한다.
 */
@Entity
@Table(name = "dashboard_templates")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DashboardTemplate extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dashboard_template_id")
    private Long dashboardTemplateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "widget_type", nullable = false, length = 30)
    private WidgetType widgetType;

    @Column(name = "title", length = 100)
    private String title;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> config;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "position", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> position;

    @Column(name = "source_query", columnDefinition = "TEXT")
    private String sourceQuery;

    @Builder
    private DashboardTemplate(
            WidgetType widgetType,
            String title,
            Map<String, Object> config,
            Map<String, Object> position,
            String sourceQuery
    ) {
        this.widgetType = widgetType;
        this.title = title;
        this.config = config;
        this.position = position;
        this.sourceQuery = sourceQuery;
    }
}

package com.ssafy.fint.domain.dashboard.entity;

import com.ssafy.fint.global.common.entity.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

import java.util.Map;

/**
 * 대시보드 위젯.
 * 자연어 쿼리(DashboardQuery) 결과를 시각화하며, 대시보드에 배치되기 전에는 dashboard 가 null 일 수 있다.
 */
@Entity
@Table(name = "dashboard_widgets")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DashboardWidget extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dashboard_widget_id")
    private Long dashboardWidgetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dashboard_query_id", nullable = false)
    private DashboardQuery dashboardQuery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dashboard_id")
    private Dashboard dashboard;

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
    private DashboardWidget(
            DashboardQuery dashboardQuery,
            Dashboard dashboard,
            WidgetType widgetType,
            String title,
            Map<String, Object> config,
            Map<String, Object> position,
            String sourceQuery
    ) {
        this.dashboardQuery = dashboardQuery;
        this.dashboard = dashboard;
        this.widgetType = widgetType;
        this.title = title;
        this.config = config;
        this.position = position;
        this.sourceQuery = sourceQuery;
    }

    public void changeTitle(String title) {
        this.title = title;
    }

    public void changeConfig(Map<String, Object> config) {
        this.config = config;
    }

    public void changePosition(Map<String, Object> position) {
        this.position = position;
    }

    public void changeWidgetType(WidgetType widgetType) {
        this.widgetType = widgetType;
    }

    public void linkToDashboard(Dashboard dashboard) {
        this.dashboard = dashboard;
    }

    public void unlinkFromDashboard() {
        this.dashboard = null;
    }
}

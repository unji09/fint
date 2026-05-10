package com.ssafy.fint.domain.dashboard.entity;

/**
 * 대시보드 위젯의 시각화 유형.
 * DashboardWidget, DashboardTemplate 양쪽에서 공유한다.
 */
public enum WidgetType {
    BAR_CHART,
    LINE_CHART,
    PIE,
    KPI,
    TABLE,
    NL_QUERY
}

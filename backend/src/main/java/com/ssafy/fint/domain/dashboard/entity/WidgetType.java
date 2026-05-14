package com.ssafy.fint.domain.dashboard.entity;

/**
 * 대시보드 위젯의 렌더링 대분류.
 * 세부 차트 종류(bar, line, pie 등)는 config.chart.type 에서 결정한다.
 */
public enum WidgetType {
    CHART,
    CARD,
    TABLE,
    LIST,
    PROGRESS,
    SUMMARY
}

package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardQuery;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import com.ssafy.fint.domain.dashboard.entity.WidgetType;
import com.ssafy.fint.domain.dashboard.repository.DashboardQueryRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardWidgetRepository;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.DashboardErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;

/**
 * 자연어 쿼리 처리 결과(FastAPI 가 Redis 에 SET 한 COMPLETED payload)를
 * dashboard_queries / dashboard_widgets 에 INSERT 한다.
 *
 * <p>SSE polling task 는 별도 스레드에서 실행되므로 self-invocation 으로는 트랜잭션이
 * 동작하지 않아 본 빈을 외부 호출 형태로 분리한다.
 * position 은 끌당해서 유저가 배치할 때까지 null 로 저장한다.(배치시 별도 api 활용 예정)
 */
@Component
@RequiredArgsConstructor
public class DashboardQueryResultWriter {

    private static final String FIELD_WIDGET_TYPE = "widget_type";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_CONFIG = "config";
    private static final String FIELD_SOURCE_QUERY = "source_query";

    private final DashboardRepository dashboardRepository;
    private final DashboardQueryRepository dashboardQueryRepository;
    private final DashboardWidgetRepository dashboardWidgetRepository;

    /**
     * 자연어 쿼리 결과를 DB에 반영한다.
     * - modifyWidgetId != null (MODIFY): 기존 위젯을 업데이트하고 position은 보존
     * - modifyWidgetId == null (ADD/CREATE): 새 위젯을 INSERT
     */
    @Transactional
    public InsertedIds persist(Long tenantId, Long userId, Long dashboardId, String inputText, Long modifyWidgetId, Map<String, Object> result) {
        Dashboard dashboard = dashboardRepository.findById(dashboardId)
                .orElseThrow(() -> new BusinessException(DashboardErrorCode.DASHBOARD_NOT_FOUND));

        if (!dashboard.getOwner().getTenant().getTenantId().equals(tenantId)) {
            throw new BusinessException(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);
        }
        if (!dashboard.getOwner().getUserId().equals(userId)) {
            throw new BusinessException(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);
        }

        DashboardQuery query = dashboardQueryRepository.save(DashboardQuery.builder()
                .dashboard(dashboard)
                .inputText(inputText)
                .result(result)
                .completedAt(OffsetDateTime.now())
                .build());

        WidgetType widgetType = WidgetType.valueOf((String) result.get(FIELD_WIDGET_TYPE));
        String title = (String) result.get(FIELD_TITLE);
        Map<String, Object> config = asMap(result.get(FIELD_CONFIG));
        String sourceQuery = (String) result.get(FIELD_SOURCE_QUERY);

        DashboardWidget widget;
        if (modifyWidgetId != null) {
            // MODIFY: 기존 위젯 업데이트 (position 유지) — dashboardId로 소유권 검증
            widget = dashboardWidgetRepository
                    .findByDashboardWidgetIdAndDashboard_DashboardId(modifyWidgetId, dashboardId)
                    .orElseThrow(() -> new BusinessException(DashboardErrorCode.WIDGET_NOT_FOUND));
            widget.changeWidgetType(widgetType);
            widget.changeTitle(title);
            widget.changeConfig(config);
            widget.changeSourceQuery(sourceQuery);
            widget.linkToQuery(query);
        } else {
            // ADD / CREATE: 새 위젯 삽입
            widget = dashboardWidgetRepository.save(DashboardWidget.builder()
                    .dashboard(dashboard)
                    .dashboardQuery(query)
                    .widgetType(widgetType)
                    .title(title)
                    .config(config)
                    .position(null)
                    .sourceQuery(sourceQuery)
                    .build());
        }

        return new InsertedIds(widget.getDashboardWidgetId(), query.getDashboardQueryId());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : null;
    }

    public record InsertedIds(Long widgetId, Long queryId) {
    }
}

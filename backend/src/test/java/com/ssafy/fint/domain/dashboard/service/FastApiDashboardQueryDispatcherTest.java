package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.AiQueryDispatchRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FastAPI dispatcher 단위 테스트.
 * - URL: {ai-server-url}/api/v1/dashboard/query 로 POST
 * - X-Tenant-Id 헤더 + JSON Content-Type
 * - 본문은 AiQueryDispatchRequest.from(command) 결과
 */
@ExtendWith(MockitoExtension.class)
class FastApiDashboardQueryDispatcherTest {

    private static final String AI_SERVER_URL = "http://localhost:8000";
    private static final String EXPECTED_URL = AI_SERVER_URL + "/api/v1/dashboard/query";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Mock private RestTemplate aiRestTemplate;

    @Test
    @DisplayName("dispatch 호출 시 FastAPI POST URL/X-Tenant-Id 헤더/JSON 본문이 모두 정확히 전송된다.")
    void dispatchSendsExpectedHttpRequest() {
        FastApiDashboardQueryDispatcher dispatcher =
                new FastApiDashboardQueryDispatcher(aiRestTemplate, AI_SERVER_URL);

        DashboardQueryDispatchCommand command = new DashboardQueryDispatchCommand(
                "trace-1",
                QueryAction.ADD,
                "주간 매출 추이",
                5L,
                1L,
                99L,
                List.of(),
                null
        );

        dispatcher.dispatch(command);

        ArgumentCaptor<HttpEntity<AiQueryDispatchRequest>> entityCaptor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(aiRestTemplate).postForObject(eq(EXPECTED_URL), entityCaptor.capture(), eq(Void.class));

        HttpEntity<AiQueryDispatchRequest> entity = entityCaptor.getValue();
        assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(entity.getHeaders().getFirst(TENANT_HEADER)).isEqualTo("1");

        AiQueryDispatchRequest body = entity.getBody();
        assertThat(body).isNotNull();
        assertThat(body.traceId()).isEqualTo("trace-1");
        assertThat(body.action()).isEqualTo("ADD");
        assertThat(body.inputText()).isEqualTo("주간 매출 추이");
        assertThat(body.dashboardId()).isEqualTo(5L);
        assertThat(body.tenantId()).isEqualTo(1L);
        assertThat(body.userId()).isEqualTo(99L);
        assertThat(body.existingWidgets()).isEmpty();
        assertThat(body.currentWidget()).isNull();
    }

    @Test
    @DisplayName("action=CREATE + existingWidgets 비어있는 command 가 정확히 직렬화되어 전송된다.")
    void dispatchCreateActionWithEmptyWidgets() {
        FastApiDashboardQueryDispatcher dispatcher =
                new FastApiDashboardQueryDispatcher(aiRestTemplate, AI_SERVER_URL);

        DashboardQueryDispatchCommand command = new DashboardQueryDispatchCommand(
                "trace-2",
                QueryAction.CREATE,
                "월별 매출 현황",
                10L,
                2L,
                50L,
                List.of(),
                null
        );

        dispatcher.dispatch(command);

        ArgumentCaptor<HttpEntity<AiQueryDispatchRequest>> entityCaptor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(aiRestTemplate).postForObject(eq(EXPECTED_URL), entityCaptor.capture(), eq(Void.class));

        AiQueryDispatchRequest body = entityCaptor.getValue().getBody();
        assertThat(body).isNotNull();
        assertThat(body.action()).isEqualTo("CREATE");
        assertThat(body.dashboardId()).isEqualTo(10L);
        assertThat(body.tenantId()).isEqualTo(2L);
        assertThat(body.userId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("existingWidgets 에 위젯 컨텍스트가 채워진 command 가 올바르게 전송된다.")
    void dispatchWithExistingWidgetContext() {
        FastApiDashboardQueryDispatcher dispatcher =
                new FastApiDashboardQueryDispatcher(aiRestTemplate, AI_SERVER_URL);

        Map<String, Object> widgetPayload = Map.of(
                "widget_type", "BAR_CHART",
                "title", "업종별 매출",
                "source_query", "SELECT industry FROM deals"
        );
        DashboardQueryDispatchCommand command = new DashboardQueryDispatchCommand(
                "trace-3",
                QueryAction.ADD,
                "분기별 추이",
                5L,
                1L,
                99L,
                List.of(widgetPayload),
                null
        );

        dispatcher.dispatch(command);

        ArgumentCaptor<HttpEntity<AiQueryDispatchRequest>> entityCaptor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(aiRestTemplate).postForObject(eq(EXPECTED_URL), entityCaptor.capture(), eq(Void.class));

        AiQueryDispatchRequest body = entityCaptor.getValue().getBody();
        assertThat(body).isNotNull();
        assertThat(body.existingWidgets()).hasSize(1);
    }

    @Test
    @DisplayName("RestTemplate 예외 발생 시 그대로 호출자에게 전파된다.")
    void dispatchPropagatesRestTemplateException() {
        FastApiDashboardQueryDispatcher dispatcher =
                new FastApiDashboardQueryDispatcher(aiRestTemplate, AI_SERVER_URL);

        when(aiRestTemplate.postForObject(eq(EXPECTED_URL), any(), eq(Void.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"));

        DashboardQueryDispatchCommand command = new DashboardQueryDispatchCommand(
                "trace-err", QueryAction.CREATE, "test", 1L, 1L, 1L, List.of(), null);

        assertThatThrownBy(() -> dispatcher.dispatch(command))
                .isInstanceOf(org.springframework.web.client.ResourceAccessException.class)
                .hasMessageContaining("Connection refused");
    }
}

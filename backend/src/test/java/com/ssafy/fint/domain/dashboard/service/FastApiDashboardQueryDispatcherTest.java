package com.ssafy.fint.domain.dashboard.service;

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

@ExtendWith(MockitoExtension.class)
class FastApiDashboardQueryDispatcherTest {

    private static final String AI_SERVER_URL = "http://localhost:8000";
    private static final String EXPECTED_URL = AI_SERVER_URL + "/api/v1/dashboard/query";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    @Mock private RestTemplate aiRestTemplate;

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureBody() {
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(aiRestTemplate).postForObject(eq(EXPECTED_URL), captor.capture(), eq(Void.class));
        return captor.getValue().getBody();
    }

    @SuppressWarnings("unchecked")
    private HttpEntity<Map<String, Object>> captureEntity() {
        ArgumentCaptor<HttpEntity<Map<String, Object>>> captor =
                ArgumentCaptor.forClass(HttpEntity.class);
        verify(aiRestTemplate).postForObject(eq(EXPECTED_URL), captor.capture(), eq(Void.class));
        return captor.getValue();
    }

    @Test
    @DisplayName("dispatch 호출 시 snake_case 키로 FastAPI POST URL/X-Tenant-Id 헤더/JSON 본문이 전송된다.")
    void dispatchSendsSnakeCaseBody() {
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

        HttpEntity<Map<String, Object>> entity = captureEntity();
        assertThat(entity.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(entity.getHeaders().getFirst(TENANT_HEADER)).isEqualTo("1");

        Map<String, Object> body = entity.getBody();
        assertThat(body).isNotNull();
        assertThat(body).containsEntry("trace_id", "trace-1");
        assertThat(body).containsEntry("action", "ADD");
        assertThat(body).containsEntry("input_text", "주간 매출 추이");
        assertThat(body).containsEntry("dashboard_id", 5L);
        assertThat(body).containsEntry("tenant_id", 1L);
        assertThat(body).containsEntry("user_id", 99L);
        assertThat((List<?>) body.get("existing_widgets")).isEmpty();
        assertThat(body.get("current_widget")).isNull();
    }

    @Test
    @DisplayName("action=CREATE + existingWidgets 비어있는 command 가 snake_case 로 전송된다.")
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

        Map<String, Object> body = captureBody();
        assertThat(body).isNotNull();
        assertThat(body).containsEntry("action", "CREATE");
        assertThat(body).containsEntry("dashboard_id", 10L);
        assertThat(body).containsEntry("tenant_id", 2L);
        assertThat(body).containsEntry("user_id", 50L);
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

        Map<String, Object> body = captureBody();
        assertThat(body).isNotNull();
        assertThat((List<?>) body.get("existing_widgets")).hasSize(1);
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

    @Test
    @DisplayName("본문에 camelCase 키가 존재하지 않고 snake_case 키만 존재한다.")
    void bodyContainsNoCamelCaseKeys() {
        FastApiDashboardQueryDispatcher dispatcher =
                new FastApiDashboardQueryDispatcher(aiRestTemplate, AI_SERVER_URL);

        DashboardQueryDispatchCommand command = new DashboardQueryDispatchCommand(
                "trace-sc", QueryAction.ADD, "테스트", 1L, 1L, 1L, List.of(), null);

        dispatcher.dispatch(command);

        Map<String, Object> body = captureBody();
        assertThat(body).isNotNull();
        assertThat(body.keySet()).containsExactlyInAnyOrder(
                "trace_id", "action", "input_text", "dashboard_id",
                "tenant_id", "user_id", "existing_widgets", "current_widget");
    }
}

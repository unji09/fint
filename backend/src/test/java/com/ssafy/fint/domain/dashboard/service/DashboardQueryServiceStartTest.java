package com.ssafy.fint.domain.dashboard.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.fint.domain.dashboard.dto.QueryStartRequest;
import com.ssafy.fint.domain.dashboard.dto.QueryStartResponse;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import com.ssafy.fint.domain.dashboard.entity.WidgetType;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardWidgetRepository;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import com.ssafy.fint.global.exception.DashboardErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 자연어 쿼리 시작(POST /dashboards/{dashboardId}/queries) 단위 테스트.
 * Redis PENDING 상태 등록 + FastAPI dispatch 호출이 한 번씩 발생하고
 * 호출자가 dashboard 의 owner 가 아니면 차단되는지 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DashboardQueryServiceStartTest {

    private static final long TENANT_ID = 1L;
    private static final long OWNER_USER_ID = 99L;
    private static final long STRANGER_USER_ID = 77L;
    private static final long DASHBOARD_ID = 5L;
    private static final long REDIS_TTL_SECONDS = 600L;
    private static final String REDIS_KEY_PREFIX = "dashboard:query:";
    private static final String INPUT_TEXT = "업종별 예상 매출 보여줘";

    @Mock private DashboardRepository dashboardRepository;
    @Mock private DashboardWidgetRepository dashboardWidgetRepository;
    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private DashboardQueryDispatcher queryDispatcher;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DashboardQueryService dashboardQueryService;

    private final CustomUserDetails owner =
            new CustomUserDetails(OWNER_USER_ID, TENANT_ID, "MEMBER");
    private final CustomUserDetails stranger =
            new CustomUserDetails(STRANGER_USER_ID, TENANT_ID, "MEMBER");

    @BeforeEach
    void setUp() {
        dashboardQueryService = new DashboardQueryService(
                dashboardRepository,
                dashboardWidgetRepository,
                redisTemplate,
                queryDispatcher,
                objectMapper
        );
    }

    @Test
    @DisplayName("위젯이 없는 대시보드는 action=CREATE 로 dispatch 되고 existing_widgets 는 비어 있다.")
    void startDispatchesCreateWhenDashboardHasNoWidgets() throws Exception {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardWidgetRepository.findAllByDashboard_DashboardId(DASHBOARD_ID))
                .thenReturn(List.of());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        QueryStartResponse res = dashboardQueryService.start(
                owner, DASHBOARD_ID, new QueryStartRequest(INPUT_TEXT));

        assertThat(res.traceId()).isNotBlank();
        assertThat(UUID.fromString(res.traceId())).isNotNull();

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).set(
                keyCaptor.capture(),
                valueCaptor.capture(),
                eq(Duration.ofSeconds(REDIS_TTL_SECONDS))
        );
        assertThat(keyCaptor.getValue()).isEqualTo(REDIS_KEY_PREFIX + res.traceId());

        Map<String, Object> payload = objectMapper.readValue(valueCaptor.getValue(), Map.class);
        assertThat(payload)
                .containsEntry("status", "PENDING")
                .containsEntry("dashboardId", (int) DASHBOARD_ID)
                .containsEntry("inputText", INPUT_TEXT)
                .containsEntry("userId", (int) OWNER_USER_ID)
                .containsEntry("tenantId", (int) TENANT_ID)
                .containsKey("requestedAt");

        ArgumentCaptor<DashboardQueryDispatchCommand> commandCaptor =
                ArgumentCaptor.forClass(DashboardQueryDispatchCommand.class);
        verify(queryDispatcher).dispatch(commandCaptor.capture());
        DashboardQueryDispatchCommand cmd = commandCaptor.getValue();
        assertThat(cmd.traceId()).isEqualTo(res.traceId());
        assertThat(cmd.action()).isEqualTo(QueryAction.CREATE);
        assertThat(cmd.inputText()).isEqualTo(INPUT_TEXT);
        assertThat(cmd.dashboardId()).isEqualTo(DASHBOARD_ID);
        assertThat(cmd.userId()).isEqualTo(OWNER_USER_ID);
        assertThat(cmd.tenantId()).isEqualTo(TENANT_ID);
        assertThat(cmd.existingWidgets()).isEmpty();
        assertThat(cmd.currentWidget()).isNull();
    }

    @Test
    @DisplayName("위젯이 있는 대시보드는 action=ADD 로 dispatch 되고 existing_widgets 에 widget_type/title/source_query/config 가 채워진다.")
    void startDispatchesAddWithFullWidgetPayloadWhenDashboardHasWidgets() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        DashboardWidget widget = DashboardWidget.builder()
                .dashboard(dashboard)
                .widgetType(WidgetType.CHART)
                .title("업종별 매출")
                .config(Map.of("colors", List.of("#fff"), "x_label", "업종"))
                .sourceQuery("SELECT industry, SUM(amount) FROM deals GROUP BY industry")
                .build();
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardWidgetRepository.findAllByDashboard_DashboardId(DASHBOARD_ID))
                .thenReturn(List.of(widget));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        dashboardQueryService.start(owner, DASHBOARD_ID, new QueryStartRequest(INPUT_TEXT));

        ArgumentCaptor<DashboardQueryDispatchCommand> commandCaptor =
                ArgumentCaptor.forClass(DashboardQueryDispatchCommand.class);
        verify(queryDispatcher).dispatch(commandCaptor.capture());
        DashboardQueryDispatchCommand cmd = commandCaptor.getValue();
        assertThat(cmd.action()).isEqualTo(QueryAction.ADD);
        assertThat(cmd.existingWidgets()).hasSize(1);
        @SuppressWarnings("unchecked")
        Map<String, Object> sentWidget = (Map<String, Object>) cmd.existingWidgets().get(0);
        assertThat(sentWidget)
                .containsEntry("widget_type", "CHART")
                .containsEntry("title", "업종별 매출")
                .containsEntry("source_query", "SELECT industry, SUM(amount) FROM deals GROUP BY industry");
        @SuppressWarnings("unchecked")
        Map<String, Object> sentConfig = (Map<String, Object>) sentWidget.get("config");
        assertThat(sentConfig)
                .containsEntry("x_label", "업종")
                .containsEntry("colors", List.of("#fff"));
    }

    @Test
    @DisplayName("dashboard 가 존재하지 않으면 DASHBOARD_NOT_FOUND 로 차단되고 Redis/Dispatcher 는 호출되지 않는다.")
    void notFoundWhenDashboardMissing() {
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardQueryService.start(
                owner, DASHBOARD_ID, new QueryStartRequest(INPUT_TEXT)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_NOT_FOUND);

        verify(redisTemplate, never()).opsForValue();
        verifyNoInteractions(queryDispatcher);
    }

    @Test
    @DisplayName("Redis 직렬화 실패 시 INTERNAL_SERVER_ERROR 로 변환되고 dispatcher 는 호출되지 않는다.")
    void redisSerializationFails() throws Exception {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        ObjectMapper failingMapper = mock(ObjectMapper.class);
        when(failingMapper.writeValueAsString(any()))
                .thenThrow(new JsonProcessingException("boom") {});
        DashboardQueryService failingService = new DashboardQueryService(
                dashboardRepository, dashboardWidgetRepository, redisTemplate, queryDispatcher, failingMapper);

        assertThatThrownBy(() -> failingService.start(
                owner, DASHBOARD_ID, new QueryStartRequest(INPUT_TEXT)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.INTERNAL_SERVER_ERROR);

        verifyNoInteractions(queryDispatcher);
    }

    @Test
    @DisplayName("dispatch 실패 시 Redis 키를 FAILED 상태로 덮어쓰고 EXTERNAL_API_FAILED 예외를 던진다.")
    void dispatchFailureMarksRedisAsFailed() throws Exception {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));
        when(dashboardWidgetRepository.findAllByDashboard_DashboardId(DASHBOARD_ID))
                .thenReturn(List.of());
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        doThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"))
                .when(queryDispatcher).dispatch(any());

        assertThatThrownBy(() -> dashboardQueryService.start(
                owner, DASHBOARD_ID, new QueryStartRequest(INPUT_TEXT)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations, times(2)).set(
                keyCaptor.capture(),
                valueCaptor.capture(),
                any(Duration.class)
        );
        String failedPayload = valueCaptor.getAllValues().get(1);
        Map<String, Object> failedState = objectMapper.readValue(failedPayload, Map.class);
        assertThat(failedState).containsEntry("status", "FAILED");
        assertThat(failedState).containsKey("error");
    }

    @Test
    @DisplayName("호출자가 dashboard owner 가 아니면 DASHBOARD_ACCESS_DENIED 로 차단된다.")
    void forbiddenWhenCallerIsNotOwner() {
        Dashboard dashboard = newDashboard(OWNER_USER_ID);
        when(dashboardRepository.findById(DASHBOARD_ID)).thenReturn(Optional.of(dashboard));

        assertThatThrownBy(() -> dashboardQueryService.start(
                stranger, DASHBOARD_ID, new QueryStartRequest(INPUT_TEXT)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.DASHBOARD_ACCESS_DENIED);

        verify(redisTemplate, never()).opsForValue();
        verifyNoInteractions(queryDispatcher);
    }

    private Dashboard newDashboard(long ownerUserId) {
        User user = User.builder()
                .tenant(newTenant())
                .role(UserRole.MEMBER)
                .name("owner")
                .passwordHash("x")
                .build();
        ReflectionTestUtils.setField(user, "userId", ownerUserId);
        Dashboard dashboard = Dashboard.builder()
                .owner(user)
                .title("개인 대시보드")
                .build();
        ReflectionTestUtils.setField(dashboard, "dashboardId", DASHBOARD_ID);
        return dashboard;
    }

    private Tenant newTenant() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + TENANT_ID).build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);
        return tenant;
    }
}

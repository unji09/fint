package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.DashboardCreateRequest;
import com.ssafy.fint.domain.dashboard.dto.DashboardCreateResponse;
import com.ssafy.fint.domain.dashboard.dto.QueryStartRequest;
import com.ssafy.fint.domain.dashboard.dto.QueryStartResponse;
import com.ssafy.fint.domain.dashboard.entity.Dashboard;
import com.ssafy.fint.domain.dashboard.entity.DashboardTemplate;
import com.ssafy.fint.domain.dashboard.entity.DashboardWidget;
import com.ssafy.fint.domain.dashboard.entity.WidgetType;
import com.ssafy.fint.domain.dashboard.repository.DashboardRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardTemplateRepository;
import com.ssafy.fint.domain.dashboard.repository.DashboardWidgetRepository;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 대시보드 생성(POST /dashboards) 단위 테스트.
 * 빈/템플릿(8개 그룹 카피)/자연어(시작 API 위임) 분기를 각각 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class DashboardServiceCreateTest {

    private static final long TENANT_ID = 1L;
    private static final long USER_ID = 99L;
    private static final long NEW_DASHBOARD_ID = 5L;
    private static final String DEFAULT_TITLE = "제목없음";

    @Mock private UserRepository userRepository;
    @Mock private DashboardRepository dashboardRepository;
    @Mock private DashboardTemplateRepository dashboardTemplateRepository;
    @Mock private DashboardWidgetRepository dashboardWidgetRepository;
    @Mock private DashboardQueryService dashboardQueryService;

    @InjectMocks private DashboardService dashboardService;

    private final CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");

    @Test
    @DisplayName("빈 입력 시 dashboards INSERT 만 발생하고 traceId 는 null 이다.")
    void emptyBodyCreatesDashboardOnly() {
        stubUserAndDashboardSave();

        DashboardCreateResponse res = dashboardService.create(
                me, new DashboardCreateRequest(null, null, null));

        assertThat(res.dashboardId()).isEqualTo(NEW_DASHBOARD_ID);
        assertThat(res.traceId()).isNull();

        verifyNoInteractions(dashboardTemplateRepository);
        verifyNoInteractions(dashboardWidgetRepository);
        verifyNoInteractions(dashboardQueryService);
    }

    @Test
    @DisplayName("title 미입력 시 디폴트 \"제목없음\" 으로 저장된다.")
    void defaultTitleWhenMissing() {
        stubUserAndDashboardSave();

        dashboardService.create(me, new DashboardCreateRequest(null, null, null));

        ArgumentCaptor<Dashboard> captor = ArgumentCaptor.forClass(Dashboard.class);
        verify(dashboardRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo(DEFAULT_TITLE);
    }

    @Test
    @DisplayName("title 입력 시 그대로 저장된다.")
    void useGivenTitle() {
        stubUserAndDashboardSave();

        dashboardService.create(me, new DashboardCreateRequest("내 영업 대시보드", null, null));

        ArgumentCaptor<Dashboard> captor = ArgumentCaptor.forClass(Dashboard.class);
        verify(dashboardRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("내 영업 대시보드");
    }

    @Test
    @DisplayName("templateId=1 → dashboard_templates 의 1~8 그룹 조회 + 위젯 8개 카피.")
    void templateGroup1CopiesEightWidgets() {
        stubUserAndDashboardSave();
        when(dashboardTemplateRepository.findByDashboardTemplateIdBetween(1L, 8L))
                .thenReturn(buildTemplateGroup(1, 8));

        dashboardService.create(me, new DashboardCreateRequest(null, 1L, null));

        verify(dashboardTemplateRepository).findByDashboardTemplateIdBetween(1L, 8L);

        ArgumentCaptor<List<DashboardWidget>> captor = ArgumentCaptor.forClass(List.class);
        verify(dashboardWidgetRepository).saveAll(captor.capture());
        List<DashboardWidget> savedWidgets = captor.getValue();
        assertThat(savedWidgets).hasSize(8);
        savedWidgets.forEach(w -> {
            assertThat(w.getDashboard().getDashboardId()).isEqualTo(NEW_DASHBOARD_ID);
            assertThat(w.getDashboardQuery()).isNull();
        });
        verifyNoInteractions(dashboardQueryService);
    }

    @Test
    @DisplayName("templateId=2 → dashboard_templates 의 9~16 그룹 조회.")
    void templateGroup2QueriesCorrectRange() {
        stubUserAndDashboardSave();
        when(dashboardTemplateRepository.findByDashboardTemplateIdBetween(9L, 16L))
                .thenReturn(buildTemplateGroup(9, 16));

        dashboardService.create(me, new DashboardCreateRequest(null, 2L, null));

        verify(dashboardTemplateRepository).findByDashboardTemplateIdBetween(9L, 16L);
    }

    @Test
    @DisplayName("templateId=3 → dashboard_templates 의 17~24 그룹 조회.")
    void templateGroup3QueriesCorrectRange() {
        stubUserAndDashboardSave();
        when(dashboardTemplateRepository.findByDashboardTemplateIdBetween(17L, 24L))
                .thenReturn(buildTemplateGroup(17, 24));

        dashboardService.create(me, new DashboardCreateRequest(null, 3L, null));

        verify(dashboardTemplateRepository).findByDashboardTemplateIdBetween(17L, 24L);
    }

    @Test
    @DisplayName("템플릿 그룹이 비어있어도 별도 검증 없이 빈 위젯 리스트로 진행한다.")
    void templateGroupEmptyResultProceeds() {
        stubUserAndDashboardSave();
        when(dashboardTemplateRepository.findByDashboardTemplateIdBetween(1L, 8L))
                .thenReturn(List.of());

        DashboardCreateResponse res = dashboardService.create(
                me, new DashboardCreateRequest(null, 1L, null));

        assertThat(res.dashboardId()).isEqualTo(NEW_DASHBOARD_ID);
        verify(dashboardWidgetRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("호출자(me) 의 userId 가 DB 에 없으면 INVALID_CREDENTIALS 로 차단되고 후속 INSERT 가 발생하지 않는다.")
    void userNotFound() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> dashboardService.create(
                me, new DashboardCreateRequest(null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS);

        verify(dashboardRepository, never()).save(any());
        verifyNoInteractions(dashboardTemplateRepository);
        verifyNoInteractions(dashboardWidgetRepository);
        verifyNoInteractions(dashboardQueryService);
    }

    @Test
    @DisplayName("inputText 있음 → DashboardQueryService.start 위임 + 응답에 traceId 포함.")
    void inputTextDelegatesToStartApi() {
        stubUserAndDashboardSave();
        when(dashboardQueryService.start(eq(me), eq(NEW_DASHBOARD_ID), any(QueryStartRequest.class)))
                .thenReturn(new QueryStartResponse("trace-abc"));

        DashboardCreateResponse res = dashboardService.create(
                me, new DashboardCreateRequest(null, null, "주간 매출 추이 어때?"));

        assertThat(res.dashboardId()).isEqualTo(NEW_DASHBOARD_ID);
        assertThat(res.traceId()).isEqualTo("trace-abc");

        ArgumentCaptor<QueryStartRequest> reqCaptor = ArgumentCaptor.forClass(QueryStartRequest.class);
        verify(dashboardQueryService).start(eq(me), eq(NEW_DASHBOARD_ID), reqCaptor.capture());
        assertThat(reqCaptor.getValue().inputText()).isEqualTo("주간 매출 추이 어때?");

        verifyNoInteractions(dashboardTemplateRepository);
        verify(dashboardWidgetRepository, never()).saveAll(any());
    }

    private void stubUserAndDashboardSave() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(newUser()));
        when(dashboardRepository.save(any(Dashboard.class))).thenAnswer(inv -> {
            Dashboard d = inv.getArgument(0);
            ReflectionTestUtils.setField(d, "dashboardId", NEW_DASHBOARD_ID);
            return d;
        });
    }

    private List<DashboardTemplate> buildTemplateGroup(long startId, long endId) {
        List<DashboardTemplate> templates = new ArrayList<>();
        IntStream.rangeClosed((int) startId, (int) endId).forEach(id -> {
            DashboardTemplate template = DashboardTemplate.builder()
                    .widgetType(WidgetType.CHART)
                    .title("template-" + id)
                    .config(Map.of("k", "v"))
                    .position(Map.of("x", 0, "y", 0))
                    .build();
            ReflectionTestUtils.setField(template, "dashboardTemplateId", (long) id);
            templates.add(template);
        });
        return templates;
    }

    private User newUser() {
        User user = User.builder()
                .tenant(newTenant())
                .role(UserRole.MEMBER)
                .name("tester")
                .passwordHash("x")
                .build();
        ReflectionTestUtils.setField(user, "userId", USER_ID);
        return user;
    }

    private Tenant newTenant() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + TENANT_ID).build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);
        return tenant;
    }
}

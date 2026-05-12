package com.ssafy.fint.domain.activity.service;

import com.ssafy.fint.domain.activity.dto.ActivityCreateRequest;
import com.ssafy.fint.domain.activity.dto.ActivityCreateResponse;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.deal.repository.PipelineStageRepository;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.ActivityErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-ACT 도메인 — 활동 등록(POST /activities) 단위 테스트.
 *
 * tenant 격리·옵션 필드 처리·시간 범위 검증을 Mockito 로 검증한다.
 * 통합 흐름(컨트롤러·DB)은 별도 SpringBootTest 로 다룬다.
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceCreateTest {

    private static final Long CURRENT_TENANT_ID = 1L;
    private static final Long OTHER_TENANT_ID = 2L;
    private static final Long CURRENT_USER_ID = 10L;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private DealRepository dealRepository;

    @Mock
    private PipelineStageRepository pipelineStageRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ActivityService activityService;

    @BeforeEach
    void setAuthentication() {
        CustomUserDetails principal = new CustomUserDetails(CURRENT_USER_ID, CURRENT_TENANT_ID, "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("모든 필드를 포함해 활동을 생성한다.")
    void createWithAllFields() {
        OffsetDateTime start = OffsetDateTime.of(2026, 4, 20, 10, 0, 0, 0, ZoneOffset.ofHours(9));
        OffsetDateTime end = start.plusHours(1);

        Deal deal = newDeal(3L);
        PipelineStage stage = newPipelineStage(5L, CURRENT_TENANT_ID, "제안");

        when(dealRepository.findByIdAndTenantId(3L, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(deal));
        when(pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(5L, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(stage));
        when(userRepository.getReferenceById(CURRENT_USER_ID))
                .thenReturn(stubUser(CURRENT_USER_ID, CURRENT_TENANT_ID));
        when(activityRepository.save(any(Activity.class)))
                .thenAnswer(invocation -> {
                    Activity a = invocation.getArgument(0);
                    ReflectionTestUtils.setField(a, "activityId", 100L);
                    return a;
                });

        ActivityCreateRequest req = new ActivityCreateRequest(
                3L,
                ActivityType.MEETING,
                "Q2 미팅",
                start,
                end,
                List.of(Map.of("name", "김영업")),
                5L,
                "고객이 예산 확인 필요"
        );

        ActivityCreateResponse res = activityService.create(req);

        assertThat(res.activityId()).isEqualTo(100L);
        assertThat(res.userId()).isEqualTo(CURRENT_USER_ID);
        assertThat(res.type()).isEqualTo(ActivityType.MEETING);
        assertThat(res.title()).isEqualTo("Q2 미팅");
        assertThat(res.startAt()).isEqualTo(start);
        assertThat(res.endAt()).isEqualTo(end);
        assertThat(res.memo()).isEqualTo("고객이 예산 확인 필요");
        assertThat(res.dealId()).isEqualTo(3L);
        assertThat(res.pipelineStage().stageId()).isEqualTo(5L);
        assertThat(res.pipelineStage().stageName()).isEqualTo("제안");
        assertThat(res.attendees()).hasSize(1);

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(captor.capture());
        assertThat(captor.getValue().getUser().getUserId()).isEqualTo(CURRENT_USER_ID);
    }

    @Test
    @DisplayName("optional 필드(dealId·pipelineStageId·attendees·memo) 누락 시에도 생성된다.")
    void createWithOnlyRequiredFields() {
        OffsetDateTime start = OffsetDateTime.now();
        OffsetDateTime end = start.plusMinutes(30);

        when(userRepository.getReferenceById(CURRENT_USER_ID))
                .thenReturn(stubUser(CURRENT_USER_ID, CURRENT_TENANT_ID));
        when(activityRepository.save(any(Activity.class)))
                .thenAnswer(invocation -> {
                    Activity a = invocation.getArgument(0);
                    ReflectionTestUtils.setField(a, "activityId", 101L);
                    return a;
                });

        ActivityCreateRequest req = new ActivityCreateRequest(
                null,
                ActivityType.CALL,
                "콜드콜",
                start,
                end,
                null,
                null,
                null
        );

        ActivityCreateResponse res = activityService.create(req);

        assertThat(res.activityId()).isEqualTo(101L);
        assertThat(res.userId()).isEqualTo(CURRENT_USER_ID);
        assertThat(res.dealId()).isNull();
        assertThat(res.pipelineStage()).isNull();
        assertThat(res.attendees()).isNull();
        assertThat(res.memo()).isNull();
        verify(dealRepository, never()).findByIdAndTenantId(any(), any());
        verify(pipelineStageRepository, never()).findByPipelineStageIdAndTenant_TenantId(any(), any());

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityRepository).save(captor.capture());
        assertThat(captor.getValue().getUser().getUserId()).isEqualTo(CURRENT_USER_ID);
    }

    @Test
    @DisplayName("다른 테넌트 소유 dealId 는 DEAL_NOT_FOUND 로 차단된다.")
    void rejectDealOfAnotherTenant() {
        OffsetDateTime start = OffsetDateTime.now();
        when(dealRepository.findByIdAndTenantId(99L, CURRENT_TENANT_ID))
                .thenReturn(Optional.empty());

        ActivityCreateRequest req = new ActivityCreateRequest(
                99L,
                ActivityType.MEETING,
                "외부 미팅",
                start,
                start.plusHours(1),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> activityService.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ActivityErrorCode.DEAL_NOT_FOUND);
        verify(activityRepository, never()).save(any());
    }

    @Test
    @DisplayName("다른 테넌트 소유 pipelineStageId 는 PIPELINE_STAGE_NOT_FOUND 로 차단된다.")
    void rejectPipelineStageOfAnotherTenant() {
        OffsetDateTime start = OffsetDateTime.now();
        when(pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(77L, CURRENT_TENANT_ID))
                .thenReturn(Optional.empty());

        ActivityCreateRequest req = new ActivityCreateRequest(
                null,
                ActivityType.MEETING,
                "스테이지 침범",
                start,
                start.plusHours(1),
                null,
                77L,
                null
        );

        assertThatThrownBy(() -> activityService.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ActivityErrorCode.PIPELINE_STAGE_NOT_FOUND);
        verify(activityRepository, never()).save(any());
    }

    @Test
    @DisplayName("endAt 이 startAt 보다 이른 경우 INVALID_TIME_RANGE 로 차단된다.")
    void rejectEndBeforeStart() {
        OffsetDateTime start = OffsetDateTime.now();

        ActivityCreateRequest req = new ActivityCreateRequest(
                null,
                ActivityType.MEETING,
                "역방향",
                start,
                start.minusMinutes(1),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> activityService.create(req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ActivityErrorCode.INVALID_TIME_RANGE);
        verify(activityRepository, never()).save(any());
    }

    private Deal newDeal(long dealId) {
        Deal deal = Deal.builder()
                .title("test deal")
                .build();
        ReflectionTestUtils.setField(deal, "dealId", dealId);
        return deal;
    }

    private PipelineStage newPipelineStage(long stageId, long tenantId, String name) {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + tenantId).build();
        ReflectionTestUtils.setField(tenant, "tenantId", tenantId);
        PipelineStage stage = PipelineStage.builder().tenant(tenant).name(name).sortOrder(1).build();
        ReflectionTestUtils.setField(stage, "pipelineStageId", stageId);
        return stage;
    }

    private User stubUser(long userId, long tenantId) {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + tenantId).build();
        ReflectionTestUtils.setField(tenant, "tenantId", tenantId);
        User user = User.builder()
                .tenant(tenant)
                .name("tester")
                .passwordHash("x")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}

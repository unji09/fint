package com.ssafy.fint.domain.activity.service;

import com.ssafy.fint.domain.activity.dto.ActivityDetailResponse;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.global.exception.ActivityErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.when;

/**
 * REQ-ACT 도메인 — 활동 상세 조회(GET /activities/{activityId}) 단위 테스트.
 * tenant 격리는 Repository 레이어 책임이라 mock 으로 빈 결과를 흘리는 것으로 검증을 대체한다.
 * 본 테스트는 service 레이어의 dealId 검증과 attendees 변환에 집중한다.
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceDetailTest {

    private static final Long ACTIVITY_ID = 10L;
    private static final Long DEAL_ID = 3L;
    private static final Long STAGE_ID = 5L;
    private static final Long TENANT_ID = 1L;

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private ActivityService activityService;

    @BeforeEach
    void setAuthentication() {
        CustomUserDetails principal = new CustomUserDetails(10L, TENANT_ID, "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("정상 조회 시 모든 필드를 변환해서 반환하고 sttStatus 는 PENDING(기본값)으로 매핑된다.")
    void detailReturnsAllFields() {
        OffsetDateTime start = OffsetDateTime.of(2026, 4, 20, 10, 0, 0, 0, ZoneOffset.ofHours(9));
        OffsetDateTime end = start.plusHours(1);

        Activity activity = newActivity(
                start,
                end,
                List.of(
                        Map.of("type", "internal", "name", "홍길동"),
                        Map.of("type", "external", "name", "김철수")
                ),
                "고객이 예산 확인 필요"
        );
        when(activityRepository.findDetail(TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));

        ActivityDetailResponse res = activityService.findDetail(ACTIVITY_ID, null);

        assertThat(res.activityId()).isEqualTo(ACTIVITY_ID);
        assertThat(res.type()).isEqualTo(ActivityType.MEETING);
        assertThat(res.title()).isEqualTo("Q2 미팅");
        assertThat(res.startAt()).isEqualTo(start);
        assertThat(res.endAt()).isEqualTo(end);
        assertThat(res.memo()).isEqualTo("고객이 예산 확인 필요");
        assertThat(res.summary()).isNull();
        assertThat(res.dealId()).isEqualTo(DEAL_ID);
        assertThat(res.pipelineStage().stageId()).isEqualTo(STAGE_ID);
        assertThat(res.pipelineStage().stageName()).isEqualTo("제안");
        assertThat(res.attendees().internal()).containsExactly("홍길동");
        assertThat(res.attendees().external()).containsExactly("김철수");
    }

    @Test
    @DisplayName("Repository 에서 비어있게 반환되면 ACTIVITY_NOT_FOUND 로 차단된다.")
    void notFoundWhenRepositoryReturnsEmpty() {
        when(activityRepository.findDetail(TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.findDetail(ACTIVITY_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ActivityErrorCode.ACTIVITY_NOT_FOUND);
    }

    @Test
    @DisplayName("dealId 쿼리 파라미터가 활동의 dealId 와 일치하면 정상 반환된다.")
    void detailPassesWhenDealIdMatches() {
        Activity activity = newActivity(OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), null, null);
        when(activityRepository.findDetail(TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));

        ActivityDetailResponse res = activityService.findDetail(ACTIVITY_ID, DEAL_ID);

        assertThat(res.dealId()).isEqualTo(DEAL_ID);
    }

    @Test
    @DisplayName("dealId 쿼리 파라미터가 활동의 dealId 와 다르면 ACTIVITY_NOT_FOUND 로 차단된다.")
    void detailRejectsWhenDealIdMismatches() {
        Activity activity = newActivity(OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), null, null);
        when(activityRepository.findDetail(TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> activityService.findDetail(ACTIVITY_ID, 999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ActivityErrorCode.ACTIVITY_NOT_FOUND);
    }

    @Test
    @DisplayName("dealId 쿼리 파라미터가 주어졌지만 활동이 딜에 연결되어 있지 않으면 ACTIVITY_NOT_FOUND 로 차단된다.")
    void detailRejectsWhenActivityHasNoDeal() {
        Activity activity = newActivityWithoutDeal();
        when(activityRepository.findDetail(TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> activityService.findDetail(ACTIVITY_ID, DEAL_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ActivityErrorCode.ACTIVITY_NOT_FOUND);
    }

    @Test
    @DisplayName("attendees 가 null 이면 internal·external 모두 빈 리스트로 응답된다.")
    void detailReturnsEmptyAttendeesWhenNull() {
        Activity activity = newActivity(OffsetDateTime.now(), OffsetDateTime.now().plusHours(1), null, null);
        when(activityRepository.findDetail(TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));

        ActivityDetailResponse res = activityService.findDetail(ACTIVITY_ID, null);

        assertThat(res.attendees().internal()).isEmpty();
        assertThat(res.attendees().external()).isEmpty();
    }

    @Test
    @DisplayName("attendees 항목에 type 키가 없으면 internal 로 분류된다.")
    void detailDefaultsAttendeeToInternalWhenTypeAbsent() {
        Activity activity = newActivity(
                OffsetDateTime.now(),
                OffsetDateTime.now().plusHours(1),
                List.of(Map.of("name", "김영업")),
                null
        );
        when(activityRepository.findDetail(TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));

        ActivityDetailResponse res = activityService.findDetail(ACTIVITY_ID, null);

        assertThat(res.attendees().internal()).containsExactly("김영업");
        assertThat(res.attendees().external()).isEmpty();
    }

    private Activity newActivity(
            OffsetDateTime start,
            OffsetDateTime end,
            List<Map<String, Object>> attendees,
            String memo
    ) {
        Activity activity = Activity.builder()
                .user(newUser(TENANT_ID))
                .deal(newDeal(DEAL_ID))
                .pipelineStage(newPipelineStage(STAGE_ID, "제안"))
                .type(ActivityType.MEETING)
                .title("Q2 미팅")
                .startAt(start)
                .endAt(end)
                .attendees(attendees)
                .memo(memo)
                .build();
        ReflectionTestUtils.setField(activity, "activityId", ACTIVITY_ID);
        return activity;
    }

    private Activity newActivityWithoutDeal() {
        Activity activity = Activity.builder()
                .user(newUser(TENANT_ID))
                .pipelineStage(newPipelineStage(STAGE_ID, "제안"))
                .type(ActivityType.MEETING)
                .title("스탠드얼론")
                .startAt(OffsetDateTime.now())
                .endAt(OffsetDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(activity, "activityId", ACTIVITY_ID);
        return activity;
    }

    private User newUser(long tenantId) {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + tenantId).build();
        ReflectionTestUtils.setField(tenant, "tenantId", tenantId);
        User user = User.builder().tenant(tenant).name("tester").passwordHash("x").build();
        ReflectionTestUtils.setField(user, "userId", 99L);
        return user;
    }

    private Deal newDeal(long dealId) {
        Deal deal = Deal.builder().title("test deal").build();
        ReflectionTestUtils.setField(deal, "dealId", dealId);
        return deal;
    }

    private PipelineStage newPipelineStage(long stageId, String name) {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + TENANT_ID).build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);
        PipelineStage stage = PipelineStage.builder().tenant(tenant).name(name).sortOrder(1).build();
        ReflectionTestUtils.setField(stage, "pipelineStageId", stageId);
        return stage;
    }
}

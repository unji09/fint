package com.ssafy.fint.domain.activity.service;

import com.ssafy.fint.domain.activity.dto.ActivityUpdateRequest;
import com.ssafy.fint.domain.activity.dto.ActivityUpdateResponse;
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
import com.ssafy.fint.global.exception.CommonErrorCode;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * REQ-ACT — 활동 부분 수정(PATCH /activities/{activityId}) 단위 테스트.
 *
 * <p>null = 미수정 / 값 = 변경 의 단순 모델. 작성자 본인 검증 · 테넌트 격리 · 시간 범위 · 빈 title 검증.</p>
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceUpdateTest {

    private static final Long CURRENT_TENANT_ID = 1L;
    private static final Long CURRENT_USER_ID = 10L;
    private static final Long OTHER_USER_ID = 11L;
    private static final Long ACTIVITY_ID = 100L;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private DealRepository dealRepository;

    @Mock
    private PipelineStageRepository pipelineStageRepository;

    @Mock
    @SuppressWarnings("unused")
    private UserRepository userRepository;

    @InjectMocks
    private ActivityService activityService;

    @BeforeEach
    void setAuthentication() {
        CustomUserDetails principal = new CustomUserDetails(CURRENT_USER_ID, CURRENT_TENANT_ID, "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
        lenient().when(activityRepository.saveAndFlush(any(Activity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("모든 필드를 한 번에 수정한다.")
    void updateAllFields() {
        OffsetDateTime origStart = OffsetDateTime.of(2026, 4, 20, 9, 0, 0, 0, ZoneOffset.ofHours(9));
        OffsetDateTime origEnd = origStart.plusHours(1);
        Activity activity = newActivityOwnedByCurrentUser(origStart, origEnd);
        when(activityRepository.findDetail(CURRENT_TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));

        OffsetDateTime newStart = origStart.plusHours(1);
        OffsetDateTime newEnd = newStart.plusMinutes(90);

        when(dealRepository.findByIdAndTenantId(33L, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(newDeal(33L)));
        when(pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(55L, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(newPipelineStage(55L, "제안")));

        ActivityUpdateRequest req = new ActivityUpdateRequest(
                ActivityType.CALL,
                "Q2 미팅 (수정)",
                newStart,
                newEnd,
                List.of(Map.of("name", "김영업")),
                55L,
                33L,
                "일정 변경됨"
        );

        ActivityUpdateResponse res = activityService.update(ACTIVITY_ID, req);

        assertThat(res.activityId()).isEqualTo(ACTIVITY_ID);
        assertThat(res.type()).isEqualTo(ActivityType.CALL);
        assertThat(res.title()).isEqualTo("Q2 미팅 (수정)");
        assertThat(res.startAt()).isEqualTo(newStart);
        assertThat(res.endAt()).isEqualTo(newEnd);
        assertThat(res.attendees()).hasSize(1);
        assertThat(res.memo()).isEqualTo("일정 변경됨");
        assertThat(res.pipelineStage().stageId()).isEqualTo(55L);
        assertThat(res.pipelineStage().stageName()).isEqualTo("제안");
        assertThat(res.dealId()).isEqualTo(33L);
    }

    @Test
    @DisplayName("null 필드는 변경되지 않는다 — memo 만 수정 시 다른 필드는 그대로 유지.")
    void nullFieldsAreNotTouched() {
        OffsetDateTime origStart = OffsetDateTime.of(2026, 4, 20, 9, 0, 0, 0, ZoneOffset.ofHours(9));
        OffsetDateTime origEnd = origStart.plusHours(1);
        Activity activity = newActivityOwnedByCurrentUser(origStart, origEnd);
        when(activityRepository.findDetail(CURRENT_TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));

        ActivityUpdateRequest req = new ActivityUpdateRequest(
                null, null, null, null, null, null, null, "memo only");

        ActivityUpdateResponse res = activityService.update(ACTIVITY_ID, req);

        assertThat(res.memo()).isEqualTo("memo only");
        assertThat(res.title()).isEqualTo("기존 제목");
        assertThat(res.type()).isEqualTo(ActivityType.MEETING);
        assertThat(res.startAt()).isEqualTo(origStart);
        assertThat(res.endAt()).isEqualTo(origEnd);
    }

    @Test
    @DisplayName("endAt 이 startAt 보다 이른 경우 INVALID_TIME_RANGE 로 차단된다.")
    void rejectEndBeforeStartFromBothFields() {
        Activity activity = newActivityOwnedByCurrentUser();
        when(activityRepository.findDetail(CURRENT_TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));

        OffsetDateTime newStart = OffsetDateTime.now();
        ActivityUpdateRequest req = new ActivityUpdateRequest(
                null, null, newStart, newStart.minusMinutes(1), null, null, null, null);

        assertThatThrownBy(() -> activityService.update(ACTIVITY_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ActivityErrorCode.INVALID_TIME_RANGE);
    }

    @Test
    @DisplayName("startAt 만 변경되어도 기존 endAt 과 비교해 역전되면 차단된다.")
    void rejectStartAfterExistingEnd() {
        OffsetDateTime origStart = OffsetDateTime.of(2026, 4, 20, 9, 0, 0, 0, ZoneOffset.ofHours(9));
        OffsetDateTime origEnd = origStart.plusHours(1);
        Activity activity = newActivityOwnedByCurrentUser(origStart, origEnd);
        when(activityRepository.findDetail(CURRENT_TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));

        ActivityUpdateRequest req = new ActivityUpdateRequest(
                null, null, origEnd.plusMinutes(1), null, null, null, null, null);

        assertThatThrownBy(() -> activityService.update(ACTIVITY_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ActivityErrorCode.INVALID_TIME_RANGE);
    }

    @Test
    @DisplayName("title 이 공백 문자열이면 BLANK_TITLE 로 차단된다.")
    void rejectBlankTitle() {
        Activity activity = newActivityOwnedByCurrentUser();
        when(activityRepository.findDetail(CURRENT_TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));

        ActivityUpdateRequest req = new ActivityUpdateRequest(
                null, "   ", null, null, null, null, null, null);

        assertThatThrownBy(() -> activityService.update(ACTIVITY_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ActivityErrorCode.BLANK_TITLE);
    }

    @Test
    @DisplayName("다른 테넌트 소유 dealId 는 DEAL_NOT_FOUND 로 차단된다.")
    void rejectDealOfAnotherTenant() {
        Activity activity = newActivityOwnedByCurrentUser();
        when(activityRepository.findDetail(CURRENT_TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(dealRepository.findByIdAndTenantId(99L, CURRENT_TENANT_ID)).thenReturn(Optional.empty());

        ActivityUpdateRequest req = new ActivityUpdateRequest(
                null, null, null, null, null, null, 99L, null);

        assertThatThrownBy(() -> activityService.update(ACTIVITY_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ActivityErrorCode.DEAL_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 테넌트 소유 pipelineStageId 는 PIPELINE_STAGE_NOT_FOUND 로 차단된다.")
    void rejectPipelineStageOfAnotherTenant() {
        Activity activity = newActivityOwnedByCurrentUser();
        when(activityRepository.findDetail(CURRENT_TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(77L, CURRENT_TENANT_ID))
                .thenReturn(Optional.empty());

        ActivityUpdateRequest req = new ActivityUpdateRequest(
                null, null, null, null, null, 77L, null, null);

        assertThatThrownBy(() -> activityService.update(ACTIVITY_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ActivityErrorCode.PIPELINE_STAGE_NOT_FOUND);
    }

    @Test
    @DisplayName("활동을 찾을 수 없으면 ACTIVITY_NOT_FOUND 로 차단된다 (다른 테넌트 포함).")
    void rejectActivityNotFound() {
        when(activityRepository.findDetail(CURRENT_TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.empty());

        ActivityUpdateRequest req = new ActivityUpdateRequest(
                null, null, null, null, null, null, null, "hi");

        assertThatThrownBy(() -> activityService.update(ACTIVITY_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ActivityErrorCode.ACTIVITY_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 테넌트의 다른 사용자 소유 활동은 FORBIDDEN 으로 차단된다.")
    void rejectAnotherUsersActivity() {
        Activity activity = newActivity(OTHER_USER_ID, CURRENT_TENANT_ID);
        when(activityRepository.findDetail(CURRENT_TENANT_ID, ACTIVITY_ID)).thenReturn(Optional.of(activity));

        ActivityUpdateRequest req = new ActivityUpdateRequest(
                null, null, null, null, null, null, null, "not mine");

        assertThatThrownBy(() -> activityService.update(ACTIVITY_ID, req))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.FORBIDDEN);
    }

    // -------------------- helpers --------------------

    private Activity newActivityOwnedByCurrentUser() {
        return newActivity(CURRENT_USER_ID, CURRENT_TENANT_ID);
    }

    private Activity newActivityOwnedByCurrentUser(OffsetDateTime startAt, OffsetDateTime endAt) {
        Activity activity = newActivity(CURRENT_USER_ID, CURRENT_TENANT_ID);
        ReflectionTestUtils.setField(activity, "startAt", startAt);
        ReflectionTestUtils.setField(activity, "endAt", endAt);
        return activity;
    }

    private Activity newActivity(long userId, long tenantId) {
        User owner = stubUser(userId, tenantId);
        Activity activity = Activity.builder()
                .user(owner)
                .type(ActivityType.MEETING)
                .title("기존 제목")
                .startAt(OffsetDateTime.of(2026, 4, 20, 9, 0, 0, 0, ZoneOffset.ofHours(9)))
                .endAt(OffsetDateTime.of(2026, 4, 20, 10, 0, 0, 0, ZoneOffset.ofHours(9)))
                .build();
        ReflectionTestUtils.setField(activity, "activityId", ACTIVITY_ID);
        return activity;
    }

    private Deal newDeal(long dealId) {
        Deal deal = Deal.builder().title("test deal").build();
        ReflectionTestUtils.setField(deal, "dealId", dealId);
        return deal;
    }

    private PipelineStage newPipelineStage(long stageId, String name) {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + CURRENT_TENANT_ID).build();
        ReflectionTestUtils.setField(tenant, "tenantId", CURRENT_TENANT_ID);
        PipelineStage stage = PipelineStage.builder().tenant(tenant).name(name).sortOrder(1).build();
        ReflectionTestUtils.setField(stage, "pipelineStageId", stageId);
        return stage;
    }

    private User stubUser(long userId, long tenantId) {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + tenantId).build();
        ReflectionTestUtils.setField(tenant, "tenantId", tenantId);
        User user = User.builder().tenant(tenant).name("tester").passwordHash("x").build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}

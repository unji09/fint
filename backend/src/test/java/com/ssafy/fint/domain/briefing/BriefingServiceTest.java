package com.ssafy.fint.domain.briefing;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.briefing.client.BriefingClient;
import com.ssafy.fint.domain.briefing.dto.BriefingResponse;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.signal.repository.AccountDartDisclosureRepository;
import com.ssafy.fint.domain.signal.repository.AccountNewsArticleRepository;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import com.ssafy.fint.global.exception.ActivityErrorCode;
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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BriefingServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 99L;
    private static final Long ACTIVITY_ID = 789L;
    private static final Long ACCOUNT_ID = 123L;
    private static final Long DEAL_ID = 456L;

    @Mock private ActivityRepository activityRepository;
    @Mock private AccountNewsArticleRepository accountNewsArticleRepository;
    @Mock private AccountDartDisclosureRepository accountDartDisclosureRepository;
    @Mock private BriefingClient briefingClient;

    @InjectMocks
    private BriefingService briefingService;

    private final CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");

    // ─────────────────── 헬퍼 ───────────────────

    private Tenant tenant() {
        Tenant t = Tenant.builder().name("테스트사").build();
        ReflectionTestUtils.setField(t, "tenantId", TENANT_ID);
        return t;
    }

    private User user(Tenant tenant) {
        User u = User.builder().tenant(tenant).role(UserRole.MEMBER).name("홍길동")
                .passwordHash("hash").build();
        ReflectionTestUtils.setField(u, "userId", USER_ID);
        return u;
    }

    private Account account() {
        Account a = Account.builder().name("삼성SDS").industry("IT서비스").build();
        ReflectionTestUtils.setField(a, "accountId", ACCOUNT_ID);
        return a;
    }

    private Deal deal(Account account) {
        Deal d = Deal.builder().account(account).title("클라우드 딜").currentPipeline("NEGOTIATION").build();
        ReflectionTestUtils.setField(d, "dealId", DEAL_ID);
        return d;
    }

    private Activity meetingActivity(User user, Deal deal) {
        Activity a = Activity.builder()
                .user(user)
                .deal(deal)
                .type(ActivityType.MEETING)
                .title("2분기 미팅")
                .startAt(OffsetDateTime.now().plusMinutes(30))
                .endAt(OffsetDateTime.now().plusMinutes(90))
                .build();
        ReflectionTestUtils.setField(a, "activityId", ACTIVITY_ID);
        return a;
    }

    private BriefingResponse sampleBriefingResponse() {
        return new BriefingResponse(List.of("딜 현황: NEGOTIATION"), List.of());
    }

    // ─────────────────── generateForActivity ───────────────────

    @Test
    @DisplayName("정상 케이스: BriefingClient 호출 후 activity.briefing 업데이트 및 WS 알림")
    void generateForActivity_success() {
        Tenant tenant = tenant();
        Activity activity = meetingActivity(user(tenant), deal(account()));

        when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACTIVITY_ID, USER_ID, TENANT_ID))
                .thenReturn(Optional.of(activity));
        when(activityRepository.findRecentMeetingsByAccountId(eq(ACCOUNT_ID), eq(TENANT_ID), any()))
                .thenReturn(Optional.empty());
        when(accountNewsArticleRepository.findRecentByAccountId(eq(ACCOUNT_ID), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(accountDartDisclosureRepository.findRecentByAccountId(eq(ACCOUNT_ID), any(String.class)))
                .thenReturn(List.of());
        when(briefingClient.generate(eq(TENANT_ID), any()))
                .thenReturn(sampleBriefingResponse());

        BriefingResponse result = briefingService.generateForActivity(ACTIVITY_ID, me);

        assertThat(result.keyPoints()).containsExactly("딜 현황: NEGOTIATION");
        assertThat(activity.getBriefing()).containsKey("key_points");
    }

    @Test
    @DisplayName("activityId가 존재하지 않으면 ACTIVITY_NOT_FOUND 예외")
    void generateForActivity_notFound() {
        when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACTIVITY_ID, USER_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> briefingService.generateForActivity(ACTIVITY_ID, me))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ActivityErrorCode.ACTIVITY_NOT_FOUND);

        verify(briefingClient, never()).generate(anyLong(), any());
    }

    @Test
    @DisplayName("MEETING 이 아닌 ActivityType 이면 MEETING_TYPE_REQUIRED 예외")
    void generateForActivity_notMeetingType() {
        Tenant tenant = tenant();
        Activity callActivity = Activity.builder()
                .user(user(tenant)).deal(deal(account()))
                .type(ActivityType.CALL)
                .title("통화").startAt(OffsetDateTime.now()).endAt(OffsetDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(callActivity, "activityId", ACTIVITY_ID);

        when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACTIVITY_ID, USER_ID, TENANT_ID))
                .thenReturn(Optional.of(callActivity));

        assertThatThrownBy(() -> briefingService.generateForActivity(ACTIVITY_ID, me))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ActivityErrorCode.MEETING_TYPE_REQUIRED);
    }

    @Test
    @DisplayName("deal 이 없으면 DEAL_NOT_LINKED 예외")
    void generateForActivity_noDeal() {
        Tenant tenant = tenant();
        Activity noDealActivity = Activity.builder()
                .user(user(tenant)).type(ActivityType.MEETING)
                .title("단독 미팅").startAt(OffsetDateTime.now()).endAt(OffsetDateTime.now().plusHours(1))
                .build();
        ReflectionTestUtils.setField(noDealActivity, "activityId", ACTIVITY_ID);

        when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACTIVITY_ID, USER_ID, TENANT_ID))
                .thenReturn(Optional.of(noDealActivity));

        assertThatThrownBy(() -> briefingService.generateForActivity(ACTIVITY_ID, me))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ActivityErrorCode.DEAL_NOT_LINKED);
    }

    // ─────────────────── generateAndSave (스케줄러 경로) ───────────────────

    @Test
    @DisplayName("스케줄러 경로: deal 없으면 briefingClient 호출 없이 skip")
    void generateAndSave_skipWhenNoDeal() {
        Tenant tenant = tenant();
        Activity noDealActivity = Activity.builder()
                .user(user(tenant)).type(ActivityType.MEETING)
                .title("단독 미팅").startAt(OffsetDateTime.now()).endAt(OffsetDateTime.now().plusHours(1))
                .build();

        briefingService.generateAndSave(noDealActivity);

        verify(briefingClient, never()).generate(anyLong(), any());
    }

    @Test
    @DisplayName("스케줄러 경로: BriefingClient 예외 발생 시 briefing 미저장")
    void generateAndSave_clientExceptionHandledGracefully() {
        Tenant tenant = tenant();
        Activity activity = meetingActivity(user(tenant), deal(account()));

        when(activityRepository.findRecentMeetingsByAccountId(eq(ACCOUNT_ID), eq(TENANT_ID), any()))
                .thenReturn(Optional.empty());
        when(accountNewsArticleRepository.findRecentByAccountId(eq(ACCOUNT_ID), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(accountDartDisclosureRepository.findRecentByAccountId(eq(ACCOUNT_ID), any(String.class)))
                .thenReturn(List.of());
        when(briefingClient.generate(anyLong(), any()))
                .thenThrow(new RuntimeException("FastAPI down"));

        briefingService.generateAndSave(activity);

        assertThat(activity.getBriefing()).isNull();
    }

    @Test
    @DisplayName("스케줄러 경로: 정상 케이스 — briefing 저장 및 WS 알림")
    void generateAndSave_success() {
        Tenant tenant = tenant();
        Activity activity = meetingActivity(user(tenant), deal(account()));

        when(activityRepository.findRecentMeetingsByAccountId(eq(ACCOUNT_ID), eq(TENANT_ID), any()))
                .thenReturn(Optional.empty());
        when(accountNewsArticleRepository.findRecentByAccountId(eq(ACCOUNT_ID), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(accountDartDisclosureRepository.findRecentByAccountId(eq(ACCOUNT_ID), any(String.class)))
                .thenReturn(List.of());
        when(briefingClient.generate(eq(TENANT_ID), any()))
                .thenReturn(sampleBriefingResponse());

        briefingService.generateAndSave(activity);

        assertThat(activity.getBriefing()).isNotNull();
    }

    @Test
    @DisplayName("BriefingRequest에 activityId, accountId, accountName이 올바르게 전달된다.")
    void generateAndSave_requestFieldsCorrect() {
        Tenant tenant = tenant();
        Activity activity = meetingActivity(user(tenant), deal(account()));

        when(activityRepository.findRecentMeetingsByAccountId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(accountNewsArticleRepository.findRecentByAccountId(any(), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(accountDartDisclosureRepository.findRecentByAccountId(any(), any(String.class)))
                .thenReturn(List.of());
        when(briefingClient.generate(any(), any()))
                .thenReturn(sampleBriefingResponse());

        briefingService.generateAndSave(activity);

        ArgumentCaptor<com.ssafy.fint.domain.briefing.dto.BriefingRequest> captor =
                ArgumentCaptor.forClass(com.ssafy.fint.domain.briefing.dto.BriefingRequest.class);
        verify(briefingClient).generate(eq(TENANT_ID), captor.capture());

        var req = captor.getValue();
        assertThat(req.activityId()).isEqualTo(ACTIVITY_ID);
        assertThat(req.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(req.accountName()).isEqualTo("삼성SDS");
        assertThat(req.industry()).isEqualTo("IT서비스");
        assertThat(req.deals()).hasSize(1);
        assertThat(req.deals().get(0).currentStage()).isEqualTo("NEGOTIATION");
    }

    @Test
    @DisplayName("attendees 중 name 이 null 인 항목은 contacts 에서 제외된다.")
    void generateAndSave_nullNameAttendeeSkipped() {
        Tenant tenant = tenant();
        Activity activity = meetingActivity(user(tenant), deal(account()));

        List<Map<String, Object>> attendees = new ArrayList<>();
        Map<String, Object> nullNameAttendee = new HashMap<>();
        nullNameAttendee.put("name", null);
        nullNameAttendee.put("position", "팀장");
        attendees.add(nullNameAttendee);
        Map<String, Object> validAttendee = new HashMap<>();
        validAttendee.put("name", "김철수");
        validAttendee.put("position", "대표");
        attendees.add(validAttendee);
        ReflectionTestUtils.setField(activity, "attendees", attendees);

        when(activityRepository.findRecentMeetingsByAccountId(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(accountNewsArticleRepository.findRecentByAccountId(any(), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(accountDartDisclosureRepository.findRecentByAccountId(any(), any(String.class)))
                .thenReturn(List.of());
        when(briefingClient.generate(any(), any()))
                .thenReturn(sampleBriefingResponse());

        briefingService.generateAndSave(activity);

        ArgumentCaptor<com.ssafy.fint.domain.briefing.dto.BriefingRequest> captor =
                ArgumentCaptor.forClass(com.ssafy.fint.domain.briefing.dto.BriefingRequest.class);
        verify(briefingClient).generate(eq(TENANT_ID), captor.capture());

        assertThat(captor.getValue().contacts()).hasSize(1);
        assertThat(captor.getValue().contacts().get(0).name()).isEqualTo("김철수");
    }
}

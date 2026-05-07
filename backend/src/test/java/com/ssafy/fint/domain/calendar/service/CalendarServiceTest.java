package com.ssafy.fint.domain.calendar.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.calendar.dto.CalendarEventDetailResponse;
import com.ssafy.fint.domain.calendar.dto.CalendarEventListResponse;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CalendarErrorCode;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 캘린더 일정 통합 조회(GET /calendar/events) 단위 테스트.
 * 현재는 activities 만 source="FINT" 로 변환해 응답하는 단계.
 * tenant 격리 자체는 Repository 레이어 책임이므로, Service 는 올바른 userId/tenantId 를 전달하는지만 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class CalendarServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long TENANT_ID = 1L;
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private CalendarService calendarService;

    @BeforeEach
    void setAuthentication() {
        CustomUserDetails principal = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("정상 조회 시 source=FINT, eventId='act-{id}' 형식으로 변환되고 deal·pipelineStage 정보가 포함된다.")
    void findEventsReturnsConvertedFintItems() {
        Activity activity = newActivityWithDeal(55L, "(주)삼성 Q2 미팅",
                OffsetDateTime.of(2026, 4, 25, 10, 0, 0, 0, KST),
                OffsetDateTime.of(2026, 4, 25, 11, 0, 0, 0, KST),
                ActivityType.MEETING, 1L, "(주)삼성전자", 3L, "제안");

        Pageable pageable = PageRequest.of(0, 20);
        when(activityRepository.searchByDateRange(eq(USER_ID), eq(TENANT_ID), any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(activity), pageable, 1L));

        CalendarEventListResponse res = calendarService.findEvents(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), pageable
        );

        assertThat(res.totalElements()).isEqualTo(1L);
        assertThat(res.content()).hasSize(1);
        CalendarEventListResponse.Item item = res.content().get(0);
        assertThat(item.eventId()).isEqualTo("act-55");
        assertThat(item.source()).isEqualTo("FINT");
        assertThat(item.title()).isEqualTo("(주)삼성 Q2 미팅");
        assertThat(item.startAt()).isEqualTo(OffsetDateTime.of(2026, 4, 25, 10, 0, 0, 0, KST));
        assertThat(item.endAt()).isEqualTo(OffsetDateTime.of(2026, 4, 25, 11, 0, 0, 0, KST));
        assertThat(item.category()).isEqualTo("미팅");
        assertThat(item.accountId()).isEqualTo(1L);
        assertThat(item.accountName()).isEqualTo("(주)삼성전자");
        assertThat(item.pipelineStage().stageId()).isEqualTo(3L);
        assertThat(item.pipelineStage().stageName()).isEqualTo("제안");
        assertThat(item.linkedActivityId()).isEqualTo(55L);
    }

    @Test
    @DisplayName("deal·pipelineStage 가 없는 활동은 accountName·accountId·pipelineStage 가 모두 null 로 매핑된다.")
    void findEventsMapsNullsWhenDealAndStageMissing() {
        Activity standalone = newActivityWithoutDeal(77L, "혼자 작성한 통화 메모", ActivityType.CALL);
        Pageable pageable = PageRequest.of(0, 20);
        when(activityRepository.searchByDateRange(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(standalone), pageable, 1L));

        CalendarEventListResponse res = calendarService.findEvents(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), pageable
        );

        CalendarEventListResponse.Item item = res.content().get(0);
        assertThat(item.eventId()).isEqualTo("act-77");
        assertThat(item.category()).isEqualTo("통화");
        assertThat(item.accountId()).isNull();
        assertThat(item.accountName()).isNull();
        assertThat(item.pipelineStage()).isNull();
    }

    @Test
    @DisplayName("endDate 가 startDate 보다 이전이면 INVALID_DATE_RANGE 로 차단되고 Repository 가 호출되지 않는다.")
    void rejectInvalidDateRange() {
        Pageable pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> calendarService.findEvents(
                LocalDate.of(2026, 4, 30), LocalDate.of(2026, 4, 1), pageable
        ))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CalendarErrorCode.INVALID_DATE_RANGE);

        verify(activityRepository, never()).searchByDateRange(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("startDate 는 KST 00:00 inclusive, endDate 는 다음 날 KST 00:00 exclusive 로 Repository 에 전달된다.")
    void convertsLocalDateRangeToKstHalfOpenInterval() {
        Pageable pageable = PageRequest.of(0, 20);
        when(activityRepository.searchByDateRange(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0L));

        calendarService.findEvents(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), pageable);

        ArgumentCaptor<OffsetDateTime> startCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> endCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(activityRepository).searchByDateRange(
                eq(USER_ID), eq(TENANT_ID), startCaptor.capture(), endCaptor.capture(), eq(pageable)
        );
        assertThat(startCaptor.getValue()).isEqualTo(OffsetDateTime.of(2026, 4, 1, 0, 0, 0, 0, KST));
        assertThat(endCaptor.getValue()).isEqualTo(OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, KST));
    }

    @Test
    @DisplayName("Repository 가 빈 페이지를 반환하면 content 도 비어 있고 totalElements 는 0 이다.")
    void emptyResultReturnsEmptyContent() {
        Pageable pageable = PageRequest.of(0, 20);
        when(activityRepository.searchByDateRange(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0L));

        CalendarEventListResponse res = calendarService.findEvents(
                LocalDate.of(2026, 4, 1), LocalDate.of(2026, 4, 30), pageable
        );

        assertThat(res.content()).isEmpty();
        assertThat(res.totalElements()).isZero();
    }

    @Test
    @DisplayName("findEvent: 정상 조회 시 attendees·memo·dealTitle 까지 포함된 상세 응답이 반환된다.")
    void findEventReturnsDetailWithAttendeesAndMemo() {
        Activity activity = newDetailActivity();
        when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(10L, USER_ID, TENANT_ID))
                .thenReturn(Optional.of(activity));

        CalendarEventDetailResponse res = calendarService.findEvent("act-10");

        assertThat(res.eventId()).isEqualTo("act-10");
        assertThat(res.source()).isEqualTo("FINT");
        assertThat(res.title()).isEqualTo("(주)삼성 Q2 미팅");
        assertThat(res.startAt()).isEqualTo(OffsetDateTime.of(2026, 4, 25, 10, 0, 0, 0, KST));
        assertThat(res.endAt()).isEqualTo(OffsetDateTime.of(2026, 4, 25, 11, 0, 0, 0, KST));
        assertThat(res.category()).isEqualTo("미팅");
        assertThat(res.accountId()).isEqualTo(1L);
        assertThat(res.accountName()).isEqualTo("(주)삼성전자");
        assertThat(res.dealId()).isEqualTo(100L);
        assertThat(res.dealTitle()).isEqualTo("ERP 도입 제안");
        assertThat(res.pipelineStage().stageId()).isEqualTo(3L);
        assertThat(res.pipelineStage().stageName()).isEqualTo("제안");
        assertThat(res.linkedActivityId()).isEqualTo(10L);
        assertThat(res.attendees().internal()).containsExactly("홍길동");
        assertThat(res.attendees().external()).containsExactly("김철수");
        assertThat(res.memo()).isEqualTo("고객이 예산 확인 필요하다고 함");
    }

    @Test
    @DisplayName("findEvent: deal·pipelineStage 가 없는 활동은 관련 필드가 모두 null 로 매핑된다.")
    void findEventMapsNullsWhenDealAndStageMissing() {
        Activity standalone = newActivityWithoutDeal(77L, "혼자 작성한 통화 메모", ActivityType.CALL);
        when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(77L, USER_ID, TENANT_ID))
                .thenReturn(Optional.of(standalone));

        CalendarEventDetailResponse res = calendarService.findEvent("act-77");

        assertThat(res.eventId()).isEqualTo("act-77");
        assertThat(res.accountId()).isNull();
        assertThat(res.accountName()).isNull();
        assertThat(res.dealId()).isNull();
        assertThat(res.dealTitle()).isNull();
        assertThat(res.pipelineStage()).isNull();
        assertThat(res.attendees().internal()).isEmpty();
        assertThat(res.attendees().external()).isEmpty();
    }

    @Test
    @DisplayName("findEvent: 본인 테넌트/사용자 범위에 없으면 EVENT_NOT_FOUND.")
    void findEventThrowsNotFoundWhenRepositoryEmpty() {
        when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(99L, USER_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> calendarService.findEvent("act-99"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CalendarErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    @DisplayName("findEvent: SecurityUtils 의 userId·tenantId 가 Repository 호출에 그대로 전달된다.")
    void findEventPassesUserAndTenantToRepository() {
        Activity activity = newActivityWithoutDeal(10L, "test", ActivityType.MEETING);
        when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(any(), any(), any()))
                .thenReturn(Optional.of(activity));

        calendarService.findEvent("act-10");

        verify(activityRepository).findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                eq(10L), eq(USER_ID), eq(TENANT_ID)
        );
    }

    @Test
    @DisplayName("findEvent: prefix 가 없는 eventId 는 INVALID_EVENT_ID.")
    void findEventRejectsMissingPrefix() {
        assertThatThrownBy(() -> calendarService.findEvent("10"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CalendarErrorCode.INVALID_EVENT_ID);

        verify(activityRepository, never())
                .findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(any(), any(), any());
    }

    @Test
    @DisplayName("findEvent: act- prefix 뒤가 0 이하 정수면 INVALID_EVENT_ID 로 차단되고 Repository 가 호출되지 않는다.")
    void findEventRejectsNonPositiveId() {
        assertThatThrownBy(() -> calendarService.findEvent("act-0"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CalendarErrorCode.INVALID_EVENT_ID);
        assertThatThrownBy(() -> calendarService.findEvent("act--1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CalendarErrorCode.INVALID_EVENT_ID);

        verify(activityRepository, never())
                .findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(any(), any(), any());
    }

    @Test
    @DisplayName("findEvent: act- prefix 뒤가 숫자가 아니면 INVALID_EVENT_ID.")
    void findEventRejectsNonNumericId() {
        assertThatThrownBy(() -> calendarService.findEvent("act-abc"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CalendarErrorCode.INVALID_EVENT_ID);

        verify(activityRepository, never())
                .findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(any(), any(), any());
    }

    private Activity newDetailActivity() {
        Activity activity = Activity.builder()
                .user(newUser())
                .deal(newDealWithTitle(1L, "(주)삼성전자", 100L, "ERP 도입 제안"))
                .pipelineStage(newPipelineStage(3L, "제안"))
                .type(ActivityType.MEETING)
                .title("(주)삼성 Q2 미팅")
                .startAt(OffsetDateTime.of(2026, 4, 25, 10, 0, 0, 0, KST))
                .endAt(OffsetDateTime.of(2026, 4, 25, 11, 0, 0, 0, KST))
                .attendees(List.of(
                        Map.of("type", "internal", "name", "홍길동"),
                        Map.of("type", "external", "name", "김철수")
                ))
                .memo("고객이 예산 확인 필요하다고 함")
                .build();
        ReflectionTestUtils.setField(activity, "activityId", 10L);
        return activity;
    }

    private Deal newDealWithTitle(long accountId, String accountName, long dealId, String dealTitle) {
        Account account = Account.builder()
                .user(newUser())
                .name(accountName)
                .industry("IT")
                .build();
        ReflectionTestUtils.setField(account, "accountId", accountId);
        Deal deal = Deal.builder().account(account).title(dealTitle).build();
        ReflectionTestUtils.setField(deal, "dealId", dealId);
        return deal;
    }

    private Activity newActivityWithDeal(
            long activityId, String title,
            OffsetDateTime startAt, OffsetDateTime endAt,
            ActivityType type,
            long accountId, String accountName,
            long stageId, String stageName
    ) {
        Activity activity = Activity.builder()
                .user(newUser())
                .deal(newDeal(accountId, accountName))
                .pipelineStage(newPipelineStage(stageId, stageName))
                .type(type)
                .title(title)
                .startAt(startAt)
                .endAt(endAt)
                .build();
        ReflectionTestUtils.setField(activity, "activityId", activityId);
        return activity;
    }

    private Activity newActivityWithoutDeal(long activityId, String title, ActivityType type) {
        Activity activity = Activity.builder()
                .user(newUser())
                .type(type)
                .title(title)
                .startAt(OffsetDateTime.of(2026, 4, 26, 14, 0, 0, 0, KST))
                .endAt(OffsetDateTime.of(2026, 4, 26, 14, 30, 0, 0, KST))
                .build();
        ReflectionTestUtils.setField(activity, "activityId", activityId);
        return activity;
    }

    private User newUser() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + TENANT_ID).build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);
        User user = User.builder().tenant(tenant).name("tester").passwordHash("x").build();
        ReflectionTestUtils.setField(user, "userId", USER_ID);
        return user;
    }

    private Deal newDeal(long accountId, String accountName) {
        Account account = Account.builder()
                .user(newUser())
                .name(accountName)
                .industry("IT")
                .build();
        ReflectionTestUtils.setField(account, "accountId", accountId);
        Deal deal = Deal.builder().account(account).title("test deal").build();
        ReflectionTestUtils.setField(deal, "dealId", 100L);
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

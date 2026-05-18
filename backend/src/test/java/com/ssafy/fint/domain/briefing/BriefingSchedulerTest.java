package com.ssafy.fint.domain.briefing;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BriefingSchedulerTest {

    @Mock private ActivityRepository activityRepository;
    @Mock private BriefingService briefingService;

    @InjectMocks
    private BriefingScheduler briefingScheduler;

    private Activity fakeMeeting(Long id) {
        Tenant tenant = Tenant.builder().name("테스트사").build();
        ReflectionTestUtils.setField(tenant, "tenantId", 1L);

        User user = User.builder().tenant(tenant).role(UserRole.MEMBER)
                .name("홍길동").passwordHash("hash").build();
        ReflectionTestUtils.setField(user, "userId", 99L);

        Account account = Account.builder().name("삼성SDS").industry("IT").build();
        ReflectionTestUtils.setField(account, "accountId", 10L);

        Deal deal = Deal.builder().account(account).title("딜").currentPipeline("INIT").build();
        ReflectionTestUtils.setField(deal, "dealId", 20L);

        Activity a = Activity.builder()
                .user(user).deal(deal).type(ActivityType.MEETING)
                .title("미팅 " + id)
                .startAt(OffsetDateTime.now().plusMinutes(30))
                .endAt(OffsetDateTime.now().plusMinutes(90))
                .build();
        ReflectionTestUtils.setField(a, "activityId", id);
        return a;
    }

    @Test
    @DisplayName("윈도우 내 MEETING 이 2개면 generateAndSave 가 2회 호출된다.")
    void triggerCallsServiceForEachMeeting() {
        List<Activity> meetings = List.of(fakeMeeting(1L), fakeMeeting(2L));
        when(activityRepository.findUpcomingMeetingsWithoutBriefing(
                eq(ActivityType.MEETING), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(meetings);

        briefingScheduler.triggerMeetingBriefings();

        verify(briefingService, times(2)).generateAndSave(any(Activity.class));
    }

    @Test
    @DisplayName("윈도우 내 대상이 없으면 generateAndSave 가 호출되지 않는다.")
    void triggerSkipsWhenNoMeetings() {
        when(activityRepository.findUpcomingMeetingsWithoutBriefing(
                eq(ActivityType.MEETING), any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of());

        briefingScheduler.triggerMeetingBriefings();

        verify(briefingService, never()).generateAndSave(any());
    }

    @Test
    @DisplayName("쿼리에 ActivityType.MEETING 과 29~31분 범위 인자가 전달된다.")
    void triggerPassesCorrectWindowToRepository() {
        when(activityRepository.findUpcomingMeetingsWithoutBriefing(
                any(), any(), any()))
                .thenReturn(List.of());

        OffsetDateTime before = OffsetDateTime.now();
        briefingScheduler.triggerMeetingBriefings();

        ArgumentCaptor<ActivityType> typeCaptor = ArgumentCaptor.forClass(ActivityType.class);
        ArgumentCaptor<OffsetDateTime> fromCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        ArgumentCaptor<OffsetDateTime> toCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);

        verify(activityRepository).findUpcomingMeetingsWithoutBriefing(
                typeCaptor.capture(), fromCaptor.capture(), toCaptor.capture());

        assertThat(typeCaptor.getValue()).isEqualTo(ActivityType.MEETING);

        OffsetDateTime expectedFrom = before.plusMinutes(29);
        OffsetDateTime expectedTo = before.plusMinutes(31);
        assertThat(fromCaptor.getValue()).isAfterOrEqualTo(expectedFrom.minusSeconds(2));
        assertThat(toCaptor.getValue()).isBeforeOrEqualTo(expectedTo.plusSeconds(2));
    }
}

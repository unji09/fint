package com.ssafy.fint.domain.ai.service;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.ai.dto.NextActionCreateRequest;
import com.ssafy.fint.domain.ai.entity.TriggerType;
import com.ssafy.fint.domain.ai.service.NextActionTriggerService.AccountSignalChange;
import com.ssafy.fint.domain.signal.entity.AccountDartDisclosure;
import com.ssafy.fint.domain.signal.entity.AccountNewsArticle;
import com.ssafy.fint.domain.signal.entity.DartDisclosure;
import com.ssafy.fint.domain.signal.entity.NewsArticle;
import com.ssafy.fint.domain.signal.repository.AccountDartDisclosureRepository;
import com.ssafy.fint.domain.signal.repository.AccountNewsArticleRepository;
import com.ssafy.fint.domain.signal.service.SignalCollectService.SignalCollectResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NextActionTriggerServiceTest {

    private static final Long TENANT_ID = 1L;

    @Mock private AiSuggestionService aiSuggestionService;
    @Mock private ActivityRepository activityRepository;
    @Mock private AccountNewsArticleRepository accountNewsArticleRepository;
    @Mock private AccountDartDisclosureRepository accountDartDisclosureRepository;
    @InjectMocks private NextActionTriggerService triggerService;

    @Nested
    @DisplayName("determineTriggerType")
    class DetermineTriggerTypeTest {

        @Test
        @DisplayName("새 뉴스만 있으면 NEWS_UPDATED")
        void newNewsOnly() {
            AccountSignalChange change = new AccountSignalChange();
            change.newNewsIds.add(1L);

            assertThat(NextActionTriggerService.determineTriggerType(change))
                    .isEqualTo(TriggerType.NEWS_UPDATED);
        }

        @Test
        @DisplayName("새 DART만 있으면 DART_UPDATED")
        void newDartOnly() {
            AccountSignalChange change = new AccountSignalChange();
            change.newDartIds.add(1L);

            assertThat(NextActionTriggerService.determineTriggerType(change))
                    .isEqualTo(TriggerType.DART_UPDATED);
        }

        @Test
        @DisplayName("새 뉴스 + 새 DART 동시에 있으면 EXTERNAL_SIGNAL_UPDATED")
        void newNewsPlusDart() {
            AccountSignalChange change = new AccountSignalChange();
            change.newNewsIds.add(1L);
            change.newDartIds.add(2L);

            assertThat(NextActionTriggerService.determineTriggerType(change))
                    .isEqualTo(TriggerType.EXTERNAL_SIGNAL_UPDATED);
        }

        @Test
        @DisplayName("기존 뉴스 매핑만 있으면 NEWS_MAPPED_TO_NEW_ACCOUNT")
        void mappedNewsOnly() {
            AccountSignalChange change = new AccountSignalChange();
            change.mappedNewsIds.add(1L);

            assertThat(NextActionTriggerService.determineTriggerType(change))
                    .isEqualTo(TriggerType.NEWS_MAPPED_TO_NEW_ACCOUNT);
        }

        @Test
        @DisplayName("기존 DART 매핑만 있으면 DART_MAPPED_TO_NEW_ACCOUNT")
        void mappedDartOnly() {
            AccountSignalChange change = new AccountSignalChange();
            change.mappedDartIds.add(1L);

            assertThat(NextActionTriggerService.determineTriggerType(change))
                    .isEqualTo(TriggerType.DART_MAPPED_TO_NEW_ACCOUNT);
        }

        @Test
        @DisplayName("기존 뉴스 매핑 + 기존 DART 매핑 동시에 있으면 EXTERNAL_SIGNAL_UPDATED")
        void mappedNewsPlusDart() {
            AccountSignalChange change = new AccountSignalChange();
            change.mappedNewsIds.add(1L);
            change.mappedDartIds.add(2L);

            assertThat(NextActionTriggerService.determineTriggerType(change))
                    .isEqualTo(TriggerType.EXTERNAL_SIGNAL_UPDATED);
        }

        @Test
        @DisplayName("새 뉴스가 기존 DART 매핑보다 우선한다")
        void newNewsTakesPriorityOverMappedDart() {
            AccountSignalChange change = new AccountSignalChange();
            change.newNewsIds.add(1L);
            change.mappedDartIds.add(2L);

            assertThat(NextActionTriggerService.determineTriggerType(change))
                    .isEqualTo(TriggerType.NEWS_UPDATED);
        }
    }

    @Nested
    @DisplayName("triggerFromCollectResult")
    class TriggerFromCollectResultTest {

        @Test
        @DisplayName("수집 결과에 영향 받은 고객사가 없으면 AI 호출하지 않는다")
        void noChangesNoCall() {
            SignalCollectResult result = emptyResult();

            triggerService.triggerFromCollectResult(TENANT_ID, result);

            verify(aiSuggestionService, never()).createNextActionBySystem(any(), any());
        }

        @Test
        @DisplayName("고객사 2개에 각각 뉴스/DART 가 매핑되면 2번 호출한다")
        void twoAccountsTwoCalls() {
            SignalCollectResult result = new SignalCollectResult(
                    2, 2, 1, List.of(),
                    Map.of(10L, List.of(100L), 20L, List.of(101L)),
                    Map.of(),
                    Map.of(20L, List.of(200L)),
                    Map.of()
            );

            when(activityRepository.findRecentMeetingsByAccountId(anyLong(), eq(TENANT_ID), any(OffsetDateTime.class)))
                    .thenReturn(List.of());
            when(accountDartDisclosureRepository.findRecentByAccountId(anyLong(), anyString()))
                    .thenReturn(List.of());
            doNothing().when(aiSuggestionService).createNextActionBySystem(any(), any());

            triggerService.triggerFromCollectResult(TENANT_ID, result);

            verify(aiSuggestionService, times(2))
                    .createNextActionBySystem(eq(TENANT_ID), any(NextActionCreateRequest.class));
        }

        @Test
        @DisplayName("한 고객사에 새 뉴스 + 새 DART → EXTERNAL_SIGNAL_UPDATED 로 호출한다")
        void combinedSignalTriggerType() {
            SignalCollectResult result = new SignalCollectResult(
                    1, 1, 1, List.of(),
                    Map.of(10L, List.of(100L)),
                    Map.of(),
                    Map.of(10L, List.of(200L)),
                    Map.of()
            );

            when(activityRepository.findRecentMeetingsByAccountId(eq(10L), eq(TENANT_ID), any(OffsetDateTime.class)))
                    .thenReturn(List.of());
            doNothing().when(aiSuggestionService).createNextActionBySystem(any(), any());

            triggerService.triggerFromCollectResult(TENANT_ID, result);

            ArgumentCaptor<NextActionCreateRequest> captor =
                    ArgumentCaptor.forClass(NextActionCreateRequest.class);
            verify(aiSuggestionService).createNextActionBySystem(eq(TENANT_ID), captor.capture());

            NextActionCreateRequest captured = captor.getValue();
            assertThat(captured.accountId()).isEqualTo(10L);
            assertThat(captured.triggerType()).isEqualTo(TriggerType.EXTERNAL_SIGNAL_UPDATED);
            assertThat(captured.newsArticleIds()).containsExactly(100L);
            assertThat(captured.dartDisclosureIds()).containsExactly(200L);
        }

        @Test
        @DisplayName("한 고객사에서 AI 호출 실패해도 다른 고객사는 정상 처리된다")
        void failureDoesNotBlockOthers() {
            SignalCollectResult result = new SignalCollectResult(
                    2, 2, 0, List.of(),
                    Map.of(10L, List.of(100L), 20L, List.of(101L)),
                    Map.of(), Map.of(), Map.of()
            );

            when(activityRepository.findRecentMeetingsByAccountId(anyLong(), eq(TENANT_ID), any(OffsetDateTime.class)))
                    .thenReturn(List.of());
            when(accountDartDisclosureRepository.findRecentByAccountId(anyLong(), anyString()))
                    .thenReturn(List.of());
            doThrow(new RuntimeException("AI server down"))
                    .doNothing()
                    .when(aiSuggestionService).createNextActionBySystem(any(), any());

            triggerService.triggerFromCollectResult(TENANT_ID, result);

            verify(aiSuggestionService, times(2))
                    .createNextActionBySystem(eq(TENANT_ID), any(NextActionCreateRequest.class));
        }

        @Test
        @DisplayName("최근 미팅이 있으면 request 에 meetingIds 가 포함된다")
        void recentMeetingIdsIncluded() {
            SignalCollectResult result = new SignalCollectResult(
                    1, 0, 0, List.of(),
                    Map.of(10L, List.of(100L)),
                    Map.of(), Map.of(), Map.of()
            );

            Activity m1 = newActivity(501L);
            Activity m2 = newActivity(502L);
            Activity m3 = newActivity(503L);
            when(activityRepository.findRecentMeetingsByAccountId(eq(10L), eq(TENANT_ID), any(OffsetDateTime.class)))
                    .thenReturn(List.of(m1, m2, m3));
            when(accountDartDisclosureRepository.findRecentByAccountId(eq(10L), anyString()))
                    .thenReturn(List.of());
            doNothing().when(aiSuggestionService).createNextActionBySystem(any(), any());

            triggerService.triggerFromCollectResult(TENANT_ID, result);

            ArgumentCaptor<NextActionCreateRequest> captor =
                    ArgumentCaptor.forClass(NextActionCreateRequest.class);
            verify(aiSuggestionService).createNextActionBySystem(eq(TENANT_ID), captor.capture());

            assertThat(captor.getValue().meetingIds()).containsExactly(501L, 502L, 503L);
        }

        @Test
        @DisplayName("최근 미팅이 없으면 meetingIds 가 빈 리스트이다")
        void noRecentMeetingsEmptyList() {
            SignalCollectResult result = new SignalCollectResult(
                    1, 0, 0, List.of(),
                    Map.of(10L, List.of(100L)),
                    Map.of(), Map.of(), Map.of()
            );

            when(activityRepository.findRecentMeetingsByAccountId(eq(10L), eq(TENANT_ID), any(OffsetDateTime.class)))
                    .thenReturn(List.of());
            when(accountDartDisclosureRepository.findRecentByAccountId(eq(10L), anyString()))
                    .thenReturn(List.of());
            doNothing().when(aiSuggestionService).createNextActionBySystem(any(), any());

            triggerService.triggerFromCollectResult(TENANT_ID, result);

            ArgumentCaptor<NextActionCreateRequest> captor =
                    ArgumentCaptor.forClass(NextActionCreateRequest.class);
            verify(aiSuggestionService).createNextActionBySystem(eq(TENANT_ID), captor.capture());

            assertThat(captor.getValue().meetingIds()).isEmpty();
        }

        private Activity newActivity(Long activityId) {
            Activity activity = Activity.builder().title("test").build();
            ReflectionTestUtils.setField(activity, "activityId", activityId);
            return activity;
        }

        private SignalCollectResult emptyResult() {
            return new SignalCollectResult(
                    0, 0, 0, List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of()
            );
        }
    }

    @Nested
    @DisplayName("supplementNewsIfNeeded / supplementDartIfNeeded")
    class SupplementTest {

        @Test
        @DisplayName("DART_UPDATED 이고 뉴스가 비어있으면 최근 7일 뉴스 최대 3개를 보충한다")
        void dartUpdated_supplementsNews() {
            when(accountNewsArticleRepository.findRecentByAccountId(eq(10L), any(OffsetDateTime.class)))
                    .thenReturn(List.of(
                            newAccountNewsArticle(301L),
                            newAccountNewsArticle(302L),
                            newAccountNewsArticle(303L),
                            newAccountNewsArticle(304L)
                    ));

            List<Long> result = triggerService.supplementNewsIfNeeded(
                    TriggerType.DART_UPDATED, List.of(), 10L);

            assertThat(result).containsExactly(301L, 302L, 303L);
        }

        @Test
        @DisplayName("DART_MAPPED_TO_NEW_ACCOUNT 이고 뉴스가 비어있으면 뉴스를 보충한다")
        void dartMapped_supplementsNews() {
            when(accountNewsArticleRepository.findRecentByAccountId(eq(10L), any(OffsetDateTime.class)))
                    .thenReturn(List.of(newAccountNewsArticle(301L)));

            List<Long> result = triggerService.supplementNewsIfNeeded(
                    TriggerType.DART_MAPPED_TO_NEW_ACCOUNT, List.of(), 10L);

            assertThat(result).containsExactly(301L);
        }

        @Test
        @DisplayName("DART 트리거여도 이미 뉴스가 있으면 보충하지 않는다")
        void dartUpdated_alreadyHasNews_noSupplement() {
            List<Long> result = triggerService.supplementNewsIfNeeded(
                    TriggerType.DART_UPDATED, List.of(100L), 10L);

            assertThat(result).containsExactly(100L);
        }

        @Test
        @DisplayName("NEWS_UPDATED 트리거에서는 뉴스 보충이 발생하지 않는다")
        void newsUpdated_noNewsSupplement() {
            List<Long> result = triggerService.supplementNewsIfNeeded(
                    TriggerType.NEWS_UPDATED, List.of(), 10L);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("NEWS_UPDATED 이고 DART가 비어있으면 최근 7일 DART 1개를 보충한다")
        void newsUpdated_supplementsDart() {
            when(accountDartDisclosureRepository.findRecentByAccountId(eq(10L), anyString()))
                    .thenReturn(List.of(
                            newAccountDartDisclosure(401L),
                            newAccountDartDisclosure(402L)
                    ));

            List<Long> result = triggerService.supplementDartIfNeeded(
                    TriggerType.NEWS_UPDATED, List.of(), 10L);

            assertThat(result).containsExactly(401L);
        }

        @Test
        @DisplayName("NEWS_MAPPED_TO_NEW_ACCOUNT 이고 DART가 비어있으면 DART를 보충한다")
        void newsMapped_supplementsDart() {
            when(accountDartDisclosureRepository.findRecentByAccountId(eq(10L), anyString()))
                    .thenReturn(List.of(newAccountDartDisclosure(401L)));

            List<Long> result = triggerService.supplementDartIfNeeded(
                    TriggerType.NEWS_MAPPED_TO_NEW_ACCOUNT, List.of(), 10L);

            assertThat(result).containsExactly(401L);
        }

        @Test
        @DisplayName("NEWS 트리거여도 이미 DART가 있으면 보충하지 않는다")
        void newsUpdated_alreadyHasDart_noSupplement() {
            List<Long> result = triggerService.supplementDartIfNeeded(
                    TriggerType.NEWS_UPDATED, List.of(200L), 10L);

            assertThat(result).containsExactly(200L);
        }

        @Test
        @DisplayName("DART_UPDATED 트리거에서는 DART 보충이 발생하지 않는다")
        void dartUpdated_noDartSupplement() {
            List<Long> result = triggerService.supplementDartIfNeeded(
                    TriggerType.DART_UPDATED, List.of(), 10L);

            assertThat(result).isEmpty();
        }

        private AccountNewsArticle newAccountNewsArticle(Long newsArticleId) {
            NewsArticle news = NewsArticle.builder().title("뉴스").build();
            ReflectionTestUtils.setField(news, "newsArticleId", newsArticleId);
            return new AccountNewsArticle(null, news);
        }

        private AccountDartDisclosure newAccountDartDisclosure(Long dartDisclosureId) {
            DartDisclosure dart = DartDisclosure.builder()
                    .corpCode("00000000").corpName("테스트").reportNm("보고서")
                    .rceptNo("00000000").rceptDt("20260519").build();
            ReflectionTestUtils.setField(dart, "dartDisclosureId", dartDisclosureId);
            return new AccountDartDisclosure(null, dart);
        }
    }
}

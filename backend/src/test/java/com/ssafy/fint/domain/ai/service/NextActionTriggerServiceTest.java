package com.ssafy.fint.domain.ai.service;

import com.ssafy.fint.domain.ai.dto.NextActionCreateRequest;
import com.ssafy.fint.domain.ai.entity.TriggerType;
import com.ssafy.fint.domain.ai.service.NextActionTriggerService.AccountSignalChange;
import com.ssafy.fint.domain.signal.service.SignalCollectService.SignalCollectResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NextActionTriggerServiceTest {

    private static final Long TENANT_ID = 1L;

    @Mock private AiSuggestionService aiSuggestionService;
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

            doThrow(new RuntimeException("AI server down"))
                    .doNothing()
                    .when(aiSuggestionService).createNextActionBySystem(any(), any());

            triggerService.triggerFromCollectResult(TENANT_ID, result);

            verify(aiSuggestionService, times(2))
                    .createNextActionBySystem(eq(TENANT_ID), any(NextActionCreateRequest.class));
        }

        private SignalCollectResult emptyResult() {
            return new SignalCollectResult(
                    0, 0, 0, List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of()
            );
        }
    }
}

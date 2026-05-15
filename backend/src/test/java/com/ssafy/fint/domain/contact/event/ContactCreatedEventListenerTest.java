package com.ssafy.fint.domain.contact.event;

import com.ssafy.fint.domain.ai.service.NextActionTriggerService;
import com.ssafy.fint.domain.signal.service.SignalCollectService;
import com.ssafy.fint.domain.signal.service.SignalCollectService.SignalCollectResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContactCreatedEventListener 단위 테스트")
class ContactCreatedEventListenerTest {

    private static final Long TENANT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long CONTACT_ID = 100L;

    @Mock private SignalCollectService signalCollectService;
    @Mock private NextActionTriggerService nextActionTriggerService;
    @InjectMocks private ContactCreatedEventListener listener;

    @Nested
    @DisplayName("정상 흐름")
    class HappyPath {

        @Test
        @DisplayName("이벤트 수신 시 signal 수집 후 AI trigger 를 순서대로 호출한다")
        void collectThenTrigger() {
            ContactCreatedEvent event = new ContactCreatedEvent(TENANT_ID, ACCOUNT_ID, CONTACT_ID);
            SignalCollectResult result = emptyResult();
            given(signalCollectService.collectAndSave(TENANT_ID)).willReturn(result);

            listener.handle(event);

            InOrder order = inOrder(signalCollectService, nextActionTriggerService);
            order.verify(signalCollectService).collectAndSave(TENANT_ID);
            order.verify(nextActionTriggerService).triggerFromCollectResult(TENANT_ID, result);
        }
    }

    @Nested
    @DisplayName("예외 처리")
    class ExceptionHandling {

        @Test
        @DisplayName("signal 수집 실패 시 AI trigger 를 호출하지 않고 예외를 삼킨다")
        void collectFailsNoTrigger() {
            ContactCreatedEvent event = new ContactCreatedEvent(TENANT_ID, ACCOUNT_ID, CONTACT_ID);
            given(signalCollectService.collectAndSave(TENANT_ID))
                    .willThrow(new RuntimeException("FastAPI down"));

            listener.handle(event);

            verify(nextActionTriggerService, never()).triggerFromCollectResult(any(), any());
        }

        @Test
        @DisplayName("AI trigger 실패 시 예외를 삼킨다")
        void triggerFailsNoException() {
            ContactCreatedEvent event = new ContactCreatedEvent(TENANT_ID, ACCOUNT_ID, CONTACT_ID);
            SignalCollectResult result = emptyResult();
            given(signalCollectService.collectAndSave(TENANT_ID)).willReturn(result);
            doThrow(new RuntimeException("AI error"))
                    .when(nextActionTriggerService).triggerFromCollectResult(eq(TENANT_ID), any());

            listener.handle(event);

            verify(signalCollectService).collectAndSave(TENANT_ID);
        }
    }

    private SignalCollectResult emptyResult() {
        return new SignalCollectResult(0, 0, 0, List.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }
}

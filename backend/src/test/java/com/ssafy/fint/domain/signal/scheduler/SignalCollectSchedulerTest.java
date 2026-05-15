package com.ssafy.fint.domain.signal.scheduler;

import com.ssafy.fint.domain.ai.service.NextActionTriggerService;
import com.ssafy.fint.domain.signal.service.SignalCollectService;
import com.ssafy.fint.domain.signal.service.SignalCollectService.SignalCollectResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignalCollectSchedulerTest {

    private static final Long SYSTEM_TENANT_ID = 1L;

    @Mock private SignalCollectService signalCollectService;
    @Mock private NextActionTriggerService nextActionTriggerService;
    @InjectMocks private SignalCollectScheduler scheduler;

    @Test
    @DisplayName("수집 후 NextActionTriggerService 가 호출된다")
    void collectThenTrigger() {
        SignalCollectResult result = new SignalCollectResult(
                2, 3, 1, List.of(),
                Map.of(10L, List.of(1L)), Map.of(),
                Map.of(20L, List.of(2L)), Map.of()
        );
        when(signalCollectService.collectAndSave(SYSTEM_TENANT_ID)).thenReturn(result);

        scheduler.collectSignals();

        verify(nextActionTriggerService).triggerFromCollectResult(eq(SYSTEM_TENANT_ID), eq(result));
    }

    @Test
    @DisplayName("비활성화 시 수집도 트리거도 실행하지 않는다")
    void disabledSkipsBoth() {
        scheduler.toggle(false);

        scheduler.collectSignals();

        verify(signalCollectService, never()).collectAndSave(any());
        verify(nextActionTriggerService, never()).triggerFromCollectResult(any(), any());
    }

    @Test
    @DisplayName("수집 실패 시 트리거가 호출되지 않는다")
    void collectFailureSkipsTrigger() {
        when(signalCollectService.collectAndSave(SYSTEM_TENANT_ID))
                .thenThrow(new RuntimeException("FastAPI down"));

        scheduler.collectSignals();

        verify(nextActionTriggerService, never()).triggerFromCollectResult(any(), any());
    }
}

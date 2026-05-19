package com.ssafy.fint.domain.account.event;

import com.ssafy.fint.domain.ai.service.NextActionTriggerService;
import com.ssafy.fint.domain.signal.service.SignalCollectService;
import com.ssafy.fint.domain.signal.service.SignalCollectService.SignalCollectResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccountCreatedEventListener {

    private final SignalCollectService signalCollectService;
    private final NextActionTriggerService nextActionTriggerService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(AccountCreatedEvent event) {
        Long tenantId = event.tenantId();
        Long accountId = event.accountId();

        log.info("[AccountCreatedEvent] starting signal collection. tenantId={} accountId={}",
                tenantId, accountId);
        try {
            SignalCollectResult result = signalCollectService.collectAndSave(tenantId, List.of(accountId));
            log.info("[AccountCreatedEvent] collection done. news={} dart={}",
                    result.newsInserted(), result.dartInserted());

            nextActionTriggerService.triggerFromCollectResult(tenantId, result);
        } catch (Exception e) {
            log.error("[AccountCreatedEvent] failed. tenantId={} accountId={}", tenantId, accountId, e);
        }
    }
}

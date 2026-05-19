package com.ssafy.fint.domain.contact.event;

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
public class ContactCreatedEventListener {

    private final SignalCollectService signalCollectService;
    private final NextActionTriggerService nextActionTriggerService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ContactCreatedEvent event) {
        Long tenantId = event.tenantId();
        Long accountId = event.accountId();

        log.info("[ContactCreatedEvent] starting signal collection. tenantId={} accountId={} contactId={}",
                tenantId, accountId, event.contactId());
        try {
            SignalCollectResult result = signalCollectService.collectAndSave(tenantId, List.of(accountId));
            log.info("[ContactCreatedEvent] collection done. news={} dart={}",
                    result.newsInserted(), result.dartInserted());

            nextActionTriggerService.triggerFromCollectResult(tenantId, result);
        } catch (Exception e) {
            log.error("[ContactCreatedEvent] failed. tenantId={} accountId={}", tenantId, accountId, e);
        }
    }
}

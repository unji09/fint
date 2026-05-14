package com.ssafy.fint.domain.signal.scheduler;

import com.ssafy.fint.domain.signal.service.SignalCollectService;
import com.ssafy.fint.domain.signal.service.SignalCollectService.SignalCollectResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalCollectScheduler {

    private static final Long SYSTEM_TENANT_ID = 1L;

    private final SignalCollectService signalCollectService;
    private final AtomicBoolean enabled = new AtomicBoolean(true);

    @Scheduled(fixedRate = 600_000) // 1시간 = 3_600_000, 6분 = 360_000, 10분 = 600_000
    public void collectSignals() {
        if (!enabled.get()) {
            log.debug("[SignalScheduler] skipped (disabled)");
            return;
        }

        log.info("[SignalScheduler] starting scheduled signal collection");
        try {
            SignalCollectResult result = signalCollectService.collectAndSave(SYSTEM_TENANT_ID);
            log.info("[SignalScheduler] done. news={} dart={} errors={}",
                    result.newsInserted(), result.dartInserted(), result.errors().size());
        } catch (Exception e) {
            log.error("[SignalScheduler] failed", e);
        }
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public boolean toggle(boolean on) {
        enabled.set(on);
        log.info("[SignalScheduler] scheduler {}", on ? "ENABLED" : "DISABLED");
        return on;
    }
}

package com.ssafy.fint.domain.signal.controller;

import com.ssafy.fint.domain.ai.service.NextActionTriggerService;
import com.ssafy.fint.domain.signal.scheduler.SignalCollectScheduler;
import com.ssafy.fint.domain.signal.service.SignalCollectService;
import com.ssafy.fint.domain.signal.service.SignalCollectService.SignalCollectResult;
import com.ssafy.fint.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Signal Collect (Test)", description = "외부 데이터 수집 테스트용 API")
@RestController
@RequestMapping("/test/signals")
@RequiredArgsConstructor
public class SignalCollectController {

    private final SignalCollectService signalCollectService;
    private final SignalCollectScheduler signalCollectScheduler;
    private final NextActionTriggerService nextActionTriggerService;

    @Operation(summary = "뉴스/DART 수집 수동 트리거",
            description = "1시간 주기 스케줄러를 즉시 실행. Swagger 테스트 전용.")
    @PostMapping("/collect")
    public ApiResponse<SignalCollectResult> collect(
            @RequestParam(defaultValue = "1") Long tenantId
    ) {
        SignalCollectResult result = signalCollectService.collectAndSave(tenantId);
        nextActionTriggerService.triggerFromCollectResult(tenantId, result);
        return ApiResponse.ok(result);
    }

    @Operation(summary = "자동 수집 스케줄러 ON/OFF",
            description = "enabled=true → 켜기, enabled=false → 끄기")
    @PostMapping("/scheduler")
    public ApiResponse<Map<String, Boolean>> toggleScheduler(
            @RequestParam boolean enabled
    ) {
        boolean result = signalCollectScheduler.toggle(enabled);
        return ApiResponse.ok(Map.of("enabled", result));
    }

    @Operation(summary = "자동 수집 스케줄러 상태 조회")
    @GetMapping("/scheduler")
    public ApiResponse<Map<String, Boolean>> schedulerStatus() {
        return ApiResponse.ok(Map.of("enabled", signalCollectScheduler.isEnabled()));
    }
}

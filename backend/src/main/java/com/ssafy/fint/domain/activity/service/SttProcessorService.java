package com.ssafy.fint.domain.activity.service;

import com.ssafy.fint.domain.activity.client.AiSttClient;
import com.ssafy.fint.domain.activity.dto.SttCallbackRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * STT 비동기 처리기.
 * <p>
 * {@link com.ssafy.fint.domain.activity.service.ActivityService#requestRecording}에서 호출되며,
 * sttExecutor 스레드풀에서 FastAPI를 동기 호출 후 결과를 {@link SttCallbackService}에 위임한다.
 * <p>
 * @Async가 동일 빈 내부에서 동작하지 않는 Spring AOP 제약으로 별도 빈으로 분리한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SttProcessorService {

    private final AiSttClient aiSttClient;
    private final SttCallbackService sttCallbackService;

    @Async("sttExecutor")
    public void process(Long activityId, Long tenantId, Long accountId, String s3Key, String language) {
        log.info("[SttProcessor] start activityId={} tenantId={}", activityId, tenantId);
        try {
            List<SttCallbackRequest.Segment> segments =
                    aiSttClient.transcribe(s3Key, tenantId, language);

            sttCallbackService.processCallback(
                    activityId,
                    new SttCallbackRequest(tenantId, accountId, segments)
            );

            log.info("[SttProcessor] completed activityId={} segments={}", activityId, segments.size());
        } catch (Exception e) {
            log.error("[SttProcessor] failed activityId={}", activityId, e);
            sttCallbackService.markFailed(activityId, tenantId);
        }
    }
}

package com.ssafy.fint.domain.activity.stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SttStreamPublisher {

    public static final String STREAM_KEY = "stt:jobs";

    private final RedisTemplate<String, String> redisTemplate;

    public void publish(Long activityId, Long tenantId, String fileKey, Long accountId) {
        Map<String, String> message = new HashMap<>();
        message.put("activityId", String.valueOf(activityId));
        message.put("tenantId", String.valueOf(tenantId));
        message.put("fileKey", fileKey);
        message.put("language", "ko");
        message.put("diarize", "true");
        if (accountId != null) {
            message.put("accountId", String.valueOf(accountId));
        }
        redisTemplate.opsForStream().add(STREAM_KEY, message);
        log.info("[SttStream] published. activityId={} tenantId={} fileKey={} accountId={}",
                activityId, tenantId, fileKey, accountId);
    }
}

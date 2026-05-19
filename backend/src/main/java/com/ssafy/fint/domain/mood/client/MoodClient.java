package com.ssafy.fint.domain.mood.client;



import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Slf4j
@Component
public class MoodClient {

    private final RestTemplate restTemplate;
    private final String pythonBaseUrl;

    public MoodClient(
        @Qualifier("aiRestTemplate") RestTemplate restTemplate,
        @Value("${ai.server-url}") String pythonBaseUrl) {
        this.restTemplate = restTemplate;
        this.pythonBaseUrl = pythonBaseUrl;
    }

    public void requestMoodAnalysis(Long activityId, Long accountId, Long tenantId, String transcript) {
        // accountId / tenantId 가 없으면 분석·콜백이 끝까지 동작하지 못한다(콜백에서 Account 조회 필요).
        // Map.of 가 null 을 거부해 NPE 가 발생하는 것을 막고, 의미 있는 경고만 남기고 호출을 건너뛴다.
        if (accountId == null || tenantId == null) {
            log.warn("[MoodClient] 분석 건너뜀: accountId 또는 tenantId 누락 activityId={} accountId={} tenantId={}",
                    activityId, accountId, tenantId);
            return;
        }
        try{
            restTemplate.postForEntity(
                pythonBaseUrl + "/api/v1/mood/analyze",
                Map.of(
                    "activity_id", activityId,
                    "account_id", accountId,
                    "tenant_id", tenantId,
                    "transcript", transcript
                ),
                Void.class
            );
            log.info("[MoodClient] 날씨 분석 요청 완료 activityId={} tenantId={}", activityId, tenantId);
        } catch (Exception e){
            log.error("[MoodClient] 날씨 분석 요청 실패 activityId={} error={}", activityId, e.getMessage());
        }
    }
}

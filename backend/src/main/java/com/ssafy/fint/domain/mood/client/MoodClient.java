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

    public void requestMoodAnalysis(Long activityId, Long accountId, String transcript) {
        try{
            restTemplate.postForEntity(
                pythonBaseUrl + "/api/v1/mood/analyze",
                Map.of(
                    "activity_id", activityId,
                    "account_id", accountId,
                    "transcript", transcript
                ),
                Void.class
            );
            log.info("[MoodClient] 날씨 분석 요청 완료 activityId={}", activityId);
        } catch (Exception e){
            log.error("[MoodClient] 날씨 분석 요청 실패 activityId={} error={}", activityId, e.getMessage());
        }
    }
}

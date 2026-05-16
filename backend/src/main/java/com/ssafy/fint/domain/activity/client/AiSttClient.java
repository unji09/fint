package com.ssafy.fint.domain.activity.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.fint.domain.activity.dto.SttCallbackRequest;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * FastAPI {@code POST /api/v1/stt/transcribe} 호출 클라이언트.
 * <p>
 * POST로 job을 제출하면 FastAPI가 즉시 job_id를 반환하고 백그라운드에서 Whisper를 처리한다.
 * Spring은 {@code GET /api/v1/stt/jobs/{job_id}}를 15초 간격으로 폴링해 완료를 기다린다.
 * 최대 대기 시간은 3시간(720회 × 15s)으로, 길이 제한 없이 녹음을 처리할 수 있다.
 */
@Slf4j
@Component
public class AiSttClient {

    private static final String STT_PATH = "/api/v1/stt/transcribe";
    private static final String STT_JOB_PATH = "/api/v1/stt/jobs/";
    private static final String TENANT_HEADER = "X-Tenant-Id";
    private static final int MAX_POLL_ATTEMPTS = 720;  // 3시간
    private static final long POLL_INTERVAL_MS = 15_000L;

    private final RestTemplate sttRestTemplate;
    private final ObjectMapper objectMapper;
    private final String aiServerUrl;

    public AiSttClient(
            @Qualifier("sttRestTemplate") RestTemplate sttRestTemplate,
            ObjectMapper objectMapper,
            @Value("${ai.server-url}") String aiServerUrl
    ) {
        this.sttRestTemplate = sttRestTemplate;
        this.objectMapper = objectMapper;
        this.aiServerUrl = aiServerUrl;
    }

    /**
     * S3 키로 오디오를 전사한다 (diarize 항상 true).
     *
     * @return 화자 분리된 세그먼트 목록
     * @throws BusinessException EXTERNAL_API_FAILED — FastAPI 오류 또는 폴링 시간 초과 시
     */
    public List<SttCallbackRequest.Segment> transcribe(String s3Key, Long tenantId, String language) {
        String jobId = submitJob(s3Key, tenantId, language);
        log.info("[AiSttClient] job submitted job_id={} s3Key={}", jobId, s3Key);
        return pollUntilComplete(jobId, tenantId);
    }

    @SuppressWarnings("unchecked")
    private String submitJob(String s3Key, Long tenantId, String language) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(TENANT_HEADER, String.valueOf(tenantId));

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of(
                    "s3_key", s3Key,
                    "language", language,
                    "diarize", true
            ));
        } catch (Exception e) {
            log.error("[AiSttClient] failed to serialize request body", e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }

        try {
            String responseBody = sttRestTemplate.postForObject(
                    aiServerUrl + STT_PATH,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );
            Map<String, Object> parsed = objectMapper.readValue(responseBody, Map.class);
            Map<String, Object> data = (Map<String, Object>) parsed.get("data");
            String jobId = (String) data.get("job_id");
            if (jobId == null || jobId.isBlank()) {
                log.error("[AiSttClient] missing job_id in response. body={}", responseBody);
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }
            return jobId;
        } catch (BusinessException e) {
            throw e;
        } catch (RestClientException e) {
            log.error("[AiSttClient] submit failed: {}", e.getMessage(), e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        } catch (Exception e) {
            log.error("[AiSttClient] failed to parse submit response", e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }
    }

    @SuppressWarnings("unchecked")
    private List<SttCallbackRequest.Segment> pollUntilComplete(String jobId, Long tenantId) {
        String url = aiServerUrl + STT_JOB_PATH + jobId;
        HttpHeaders headers = new HttpHeaders();
        headers.set(TENANT_HEADER, String.valueOf(tenantId));
        HttpEntity<Void> request = new HttpEntity<>(headers);

        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "STT 처리 중단");
            }

            try {
                ResponseEntity<String> response = sttRestTemplate.exchange(
                        url, HttpMethod.GET, request, String.class
                );
                Map<String, Object> parsed = objectMapper.readValue(response.getBody(), Map.class);
                Map<String, Object> data = (Map<String, Object>) parsed.get("data");
                String status = (String) data.get("status");

                if ("COMPLETED".equals(status)) {
                    List<Map<String, Object>> rawSegments =
                            (List<Map<String, Object>>) data.get("segments");
                    log.info("[AiSttClient] job completed job_id={} segments={}", jobId, rawSegments.size());
                    return rawSegments.stream()
                            .map(seg -> new SttCallbackRequest.Segment(
                                    (String) seg.get("text"),
                                    (String) seg.get("speaker_id"),
                                    (Integer) seg.get("start_ms"),
                                    (Integer) seg.get("end_ms")
                            ))
                            .toList();
                }

                if ("FAILED".equals(status)) {
                    log.error("[AiSttClient] job failed job_id={} error={}", jobId, data.get("error"));
                    throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
                }

                log.info("[AiSttClient] polling job_id={} attempt={}/{}", jobId, attempt, MAX_POLL_ATTEMPTS);

            } catch (BusinessException e) {
                throw e;
            } catch (RestClientException e) {
                log.error("[AiSttClient] poll request failed: {}", e.getMessage(), e);
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            } catch (Exception e) {
                log.error("[AiSttClient] failed to parse poll response", e);
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }
        }

        log.error("[AiSttClient] polling timeout job_id={}", jobId);
        throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED, "STT 처리 시간 초과");
    }
}

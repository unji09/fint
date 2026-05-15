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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * FastAPI {@code POST /api/v1/stt/transcribe} 호출 클라이언트.
 * <p>
 * Spring이 @Async 스레드에서 호출하며, 전사 결과를 동기적으로 반환받는다.
 * read timeout은 300s (sttRestTemplate 설정).
 */
@Slf4j
@Component
public class AiSttClient {

    private static final String STT_PATH = "/api/v1/stt/transcribe";
    private static final String TENANT_HEADER = "X-Tenant-Id";

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
     * @throws BusinessException EXTERNAL_API_FAILED — FastAPI 오류 시
     */
    @SuppressWarnings("unchecked")
    public List<SttCallbackRequest.Segment> transcribe(String s3Key, Long tenantId, String language) {
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

        String url = aiServerUrl + STT_PATH;
        log.info("[AiSttClient] POST {} tenantId={} s3Key={}", url, tenantId, s3Key);

        try {
            String responseBody = sttRestTemplate.postForObject(
                    url,
                    new HttpEntity<>(requestBody, headers),
                    String.class
            );

            if (responseBody == null || responseBody.isBlank()) {
                log.error("[AiSttClient] empty response body");
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }

            Map<String, Object> parsed = objectMapper.readValue(responseBody, Map.class);
            Map<String, Object> data = (Map<String, Object>) parsed.get("data");
            if (data == null) {
                log.error("[AiSttClient] no data field in response. body={}", responseBody);
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }

            List<Map<String, Object>> rawSegments = (List<Map<String, Object>>) data.get("segments");
            if (rawSegments == null) {
                log.error("[AiSttClient] no segments field in data. data={}", data);
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }

            return rawSegments.stream()
                    .map(seg -> new SttCallbackRequest.Segment(
                            (String) seg.get("text"),
                            (String) seg.get("speaker_id"),
                            (Integer) seg.get("start_ms"),
                            (Integer) seg.get("end_ms")
                    ))
                    .toList();

        } catch (RestClientException e) {
            log.error("[AiSttClient] FastAPI call failed: {}", e.getMessage(), e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[AiSttClient] failed to parse FastAPI response", e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }
    }
}

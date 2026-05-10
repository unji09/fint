package com.ssafy.fint.domain.contact.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * FastAPI {@code POST /api/v1/ocr/business-card} 호출 클라이언트.
 * <p>
 * 인증은 {@code X-Tenant-Id} 헤더로 수행한다 (내부망 호출).
 * <p>
 * 요청·응답 직렬화는 모두 Jackson 2 {@link ObjectMapper} 로 수행하고,
 * RestTemplate 에는 raw {@code String} 본문을 넘긴다.
 * Spring Boot 4 에서 Jackson 2/3 메시지 컨버터가 혼용될 경우
 * {@code Map}/record 본문이 빈 바디로 전송되는 문제를 회피한다.
 */
@Slf4j
@Component
public class BusinessCardOcrClient {

    private static final String OCR_PATH = "/api/v1/ocr/business-card";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final RestTemplate ocrRestTemplate;
    private final ObjectMapper objectMapper;
    private final String aiServerUrl;

    public BusinessCardOcrClient(
            @Qualifier("ocrRestTemplate") RestTemplate ocrRestTemplate,
            ObjectMapper objectMapper,
            @Value("${ai.server-url}") String aiServerUrl
    ) {
        this.ocrRestTemplate = ocrRestTemplate;
        this.objectMapper = objectMapper;
        this.aiServerUrl = aiServerUrl;
    }

    public BusinessCardOcrApiResponse.Data extract(Long tenantId, String s3Key) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(TENANT_HEADER, String.valueOf(tenantId));

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of("s3_key", s3Key));
        } catch (Exception e) {
            log.error("[OCR] failed to serialize request body", e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        String url = aiServerUrl + OCR_PATH;
        log.info("[OCR] POST {} tenantId={} body={}", url, tenantId, requestBody);

        try {
            ResponseEntity<String> response = ocrRestTemplate.postForEntity(url, request, String.class);

            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                log.error("[OCR] empty body. status={}", response.getStatusCode());
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }

            BusinessCardOcrApiResponse parsed =
                    objectMapper.readValue(responseBody, BusinessCardOcrApiResponse.class);
            if (parsed.data() == null) {
                log.error("[OCR] no data field. status={} body={}", response.getStatusCode(), responseBody);
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }
            return parsed.data();
        } catch (RestClientException e) {
            // 4xx/5xx, 네트워크 오류 모두 포함
            log.error("[OCR] FastAPI call failed: {}", e.getMessage(), e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[OCR] failed to parse FastAPI response", e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }
    }
}

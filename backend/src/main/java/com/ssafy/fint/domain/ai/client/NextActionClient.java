package com.ssafy.fint.domain.ai.client;

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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class NextActionClient {

    private static final String NEXT_ACTION_PATH = "/api/v1/ai/next-actions";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final RestTemplate aiRestTemplate;
    private final ObjectMapper objectMapper;
    private final String aiServerUrl;

    public NextActionClient(
            @Qualifier("aiRestTemplate") RestTemplate aiRestTemplate,
            ObjectMapper objectMapper,
            @Value("${ai.server-url}") String aiServerUrl
    ) {
        this.aiRestTemplate = aiRestTemplate;
        this.objectMapper = objectMapper;
        this.aiServerUrl = aiServerUrl;
    }

    public NextActionAiResponse generate(Long tenantId, Long accountId, String context) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(TENANT_HEADER, String.valueOf(tenantId));

        Map<String, Object> body = new HashMap<>();
        body.put("account_id", accountId);
        if (context != null) {
            body.put("context", context);
        }

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("[NextAction] failed to serialize request body", e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        String url = aiServerUrl + NEXT_ACTION_PATH;
        log.info("[NextAction] POST {} tenantId={} accountId={}", url, tenantId, accountId);

        try {
            ResponseEntity<String> response = aiRestTemplate.postForEntity(url, request, String.class);

            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                log.error("[NextAction] empty body. status={}", response.getStatusCode());
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }

            NextActionAiResponse parsed = objectMapper.readValue(responseBody, NextActionAiResponse.class);
            if (parsed.title() == null || parsed.pipelineStageId() == null) {
                log.error("[NextAction] required fields missing. status={} body={}", response.getStatusCode(), responseBody);
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }
            return parsed;
        } catch (RestClientException e) {
            log.error("[NextAction] FastAPI call failed: {}", e.getMessage(), e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[NextAction] failed to parse FastAPI response", e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }
    }
}

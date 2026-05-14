package com.ssafy.fint.domain.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.fint.domain.ai.dto.NextActionCreateRequest;
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

import com.fasterxml.jackson.core.type.TypeReference;

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

    public List<NextActionAiResponse> generate(Long tenantId, NextActionCreateRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(TENANT_HEADER, String.valueOf(tenantId));

        Map<String, Object> body = new HashMap<>();
        body.put("account_id", request.accountId());
        body.put("trigger_type", request.triggerType().name());
        if (request.newsArticleIds() != null && !request.newsArticleIds().isEmpty()) {
            body.put("news_article_ids", request.newsArticleIds());
        }
        if (request.dartDisclosureIds() != null && !request.dartDisclosureIds().isEmpty()) {
            body.put("dart_disclosure_ids", request.dartDisclosureIds());
        }
        if (request.meetingId() != null) {
            body.put("meeting_id", request.meetingId());
        }
        if (request.context() != null) {
            body.put("context", request.context());
        }

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.error("[NextAction] failed to serialize request body", e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }

        HttpEntity<String> httpRequest = new HttpEntity<>(requestBody, headers);
        String url = aiServerUrl + NEXT_ACTION_PATH;
        log.info("[NextAction] POST {} tenantId={} accountId={} triggerType={}",
                url, tenantId, request.accountId(), request.triggerType());

        try {
            ResponseEntity<String> response = aiRestTemplate.postForEntity(url, httpRequest, String.class);

            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                log.error("[NextAction] empty body. status={}", response.getStatusCode());
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }

            List<NextActionAiResponse> parsed = objectMapper.readValue(
                    responseBody, new TypeReference<>() {});
            if (parsed.isEmpty()) {
                log.error("[NextAction] empty list returned. body={}", responseBody);
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }
            for (NextActionAiResponse item : parsed) {
                if (item.action() == null || item.pipelineStageId() == null) {
                    log.error("[NextAction] required fields missing in item. body={}", responseBody);
                    throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
                }
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

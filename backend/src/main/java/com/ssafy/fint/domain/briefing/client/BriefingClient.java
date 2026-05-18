package com.ssafy.fint.domain.briefing.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.fint.domain.briefing.dto.BriefingRequest;
import com.ssafy.fint.domain.briefing.dto.BriefingResponse;
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

@Slf4j
@Component
public class BriefingClient {

    private static final String BRIEFING_PATH = "/api/v1/ai/briefing";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final RestTemplate briefingRestTemplate;
    private final ObjectMapper objectMapper;
    private final String aiServerUrl;

    public BriefingClient(
            @Qualifier("briefingRestTemplate") RestTemplate briefingRestTemplate,
            ObjectMapper objectMapper,
            @Value("${ai.server-url}") String aiServerUrl
    ) {
        this.briefingRestTemplate = briefingRestTemplate;
        this.objectMapper = objectMapper;
        this.aiServerUrl = aiServerUrl;
    }

    public BriefingResponse generate(Long tenantId, BriefingRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(TENANT_HEADER, String.valueOf(tenantId));

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            log.error("[Briefing] failed to serialize request body", e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }

        HttpEntity<String> httpRequest = new HttpEntity<>(requestBody, headers);
        String url = aiServerUrl + BRIEFING_PATH;
        log.info("[Briefing] POST {} tenantId={} activityId={}", url, tenantId, request.activityId());

        try {
            String responseBody = briefingRestTemplate.postForObject(url, httpRequest, String.class);

            if (responseBody == null || responseBody.isBlank()) {
                log.error("[Briefing] empty response body. tenantId={} activityId={}", tenantId, request.activityId());
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }

            Map<String, Object> wrapper = objectMapper.readValue(responseBody, new TypeReference<>() {});
            Object data = wrapper.get("data");
            if (data == null) {
                log.error("[Briefing] 'data' field missing in response. body={}", responseBody);
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }

            return objectMapper.convertValue(data, BriefingResponse.class);
        } catch (RestClientException e) {
            log.error("[Briefing] FastAPI call failed: {}", e.getMessage(), e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Briefing] failed to parse FastAPI response", e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }
    }
}

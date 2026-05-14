package com.ssafy.fint.domain.signal.client;

import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class SignalCollectClient {

    private static final String COLLECT_PATH = "/api/v1/signals/collect";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final RestTemplate signalRestTemplate;
    private final String aiServerUrl;

    public SignalCollectClient(
            @Qualifier("signalRestTemplate") RestTemplate signalRestTemplate,
            @Value("${ai.server-url}") String aiServerUrl
    ) {
        this.signalRestTemplate = signalRestTemplate;
        this.aiServerUrl = aiServerUrl;
    }

    public SignalCollectResponse collect(Long tenantId, String source, boolean includeEmbeddings) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TENANT_HEADER, String.valueOf(tenantId));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", source);
        body.put("include_embeddings", includeEmbeddings);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        String url = aiServerUrl + COLLECT_PATH;
        log.info("[SignalCollect] POST {} tenantId={} source={}", url, tenantId, source);

        try {
            ResponseEntity<Map<String, Object>> response = signalRestTemplate.exchange(
                    url, HttpMethod.POST, request,
                    new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null) {
                log.error("[SignalCollect] empty body. status={}", response.getStatusCode());
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }

            return SignalCollectResponse.from(responseBody);
        } catch (RestClientException e) {
            log.error("[SignalCollect] FastAPI call failed: {}", e.getMessage(), e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SignalCollect] failed to parse response", e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }
    }
}

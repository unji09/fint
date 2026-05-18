package com.ssafy.fint.domain.account.client;

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

@Slf4j
@Component
public class CompanyEnrichClient {

    private static final String ENRICH_PATH = "/api/v1/company/enrich";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final RestTemplate aiRestTemplate;
    private final ObjectMapper objectMapper;
    private final String aiServerUrl;

    public CompanyEnrichClient(
            @Qualifier("aiRestTemplate") RestTemplate aiRestTemplate,
            ObjectMapper objectMapper,
            @Value("${ai.server-url}") String aiServerUrl
    ) {
        this.aiRestTemplate = aiRestTemplate;
        this.objectMapper = objectMapper;
        this.aiServerUrl = aiServerUrl;
    }

    public CompanyEnrichApiResponse.Data enrich(Long tenantId, String companyName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set(TENANT_HEADER, String.valueOf(tenantId));

        String requestBody;
        try {
            requestBody = objectMapper.writeValueAsString(Map.of("company_name", companyName));
        } catch (Exception e) {
            log.error("[CompanyEnrich] failed to serialize request body", e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }

        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
        String url = aiServerUrl + ENRICH_PATH;
        log.info("[CompanyEnrich] POST {} tenantId={} companyName={}", url, tenantId, companyName);

        try {
            ResponseEntity<String> response = aiRestTemplate.postForEntity(url, request, String.class);

            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                log.error("[CompanyEnrich] empty body. status={}", response.getStatusCode());
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }

            CompanyEnrichApiResponse parsed =
                    objectMapper.readValue(responseBody, CompanyEnrichApiResponse.class);
            if (parsed.data() == null) {
                log.error("[CompanyEnrich] no data field. body={}", responseBody);
                throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
            }
            return parsed.data();
        } catch (RestClientException e) {
            log.error("[CompanyEnrich] FastAPI call failed: {}", e.getMessage(), e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[CompanyEnrich] failed to parse FastAPI response", e);
            throw new BusinessException(CommonErrorCode.EXTERNAL_API_FAILED);
        }
    }
}

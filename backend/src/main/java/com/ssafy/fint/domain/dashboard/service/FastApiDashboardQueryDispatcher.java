package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.domain.dashboard.dto.AiQueryDispatchRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * FastAPI {@code POST /api/v1/dashboard/query} 호출로 자연어 쿼리 작업을 위임한다.
 * FastAPI 는 traceId 를 즉시 echo 한 뒤 background task 로 처리하므로
 * 본 호출은 fire-and-forget 으로 응답 본문을 무시한다 (HTTP 상태 코드만 신뢰).
 */
@Component
public class FastApiDashboardQueryDispatcher implements DashboardQueryDispatcher {

    private static final String DISPATCH_PATH = "/api/v1/dashboard/query";
    private static final String TENANT_HEADER = "X-Tenant-Id";

    private final RestTemplate aiRestTemplate;
    private final String aiServerUrl;

    public FastApiDashboardQueryDispatcher(
            @Qualifier("aiRestTemplate") RestTemplate aiRestTemplate,
            @Value("${ai.server-url}") String aiServerUrl
    ) {
        this.aiRestTemplate = aiRestTemplate;
        this.aiServerUrl = aiServerUrl;
    }

    @Override
    public void dispatch(DashboardQueryDispatchCommand command) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(TENANT_HEADER, String.valueOf(command.tenantId()));

        HttpEntity<AiQueryDispatchRequest> request = new HttpEntity<>(
                AiQueryDispatchRequest.from(command), headers);

        aiRestTemplate.postForObject(aiServerUrl + DISPATCH_PATH, request, Void.class);
    }
}

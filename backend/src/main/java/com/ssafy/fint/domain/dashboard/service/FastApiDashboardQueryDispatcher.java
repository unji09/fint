package com.ssafy.fint.domain.dashboard.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

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

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trace_id", command.traceId());
        body.put("action", command.action().name());
        body.put("input_text", command.inputText());
        body.put("dashboard_id", command.dashboardId());
        body.put("tenant_id", command.tenantId());
        body.put("user_id", command.userId());
        body.put("existing_widgets", command.existingWidgets());
        body.put("current_widget", command.currentWidget());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        aiRestTemplate.postForObject(aiServerUrl + DISPATCH_PATH, request, Void.class);
    }
}

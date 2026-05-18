package com.ssafy.fint.domain.briefing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.fint.domain.briefing.client.BriefingClient;
import com.ssafy.fint.domain.briefing.dto.BriefingRequest;
import com.ssafy.fint.domain.briefing.dto.BriefingResponse;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BriefingClientTest {

    private static final String AI_SERVER_URL = "http://ai-server:8000";
    private static final Long TENANT_ID = 1L;

    @Mock private RestTemplate briefingRestTemplate;

    private BriefingClient briefingClient;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        briefingClient = new BriefingClient(briefingRestTemplate, objectMapper, AI_SERVER_URL);
    }

    private BriefingRequest sampleRequest() {
        return new BriefingRequest(
                789L, "2분기 계약 갱신 논의", "2026-05-16T14:00:00+09:00",
                123L, "삼성SDS", "IT서비스",
                null, null, null,
                List.of(), List.of(), List.of(), List.of(), null
        );
    }

    @Test
    @DisplayName("정상 응답 시 BriefingResponse 로 역직렬화된다.")
    void parseValidResponse() {
        String responseJson = """
                {
                  "status": 200,
                  "message": "success",
                  "data": {
                    "key_points": ["딜 현황: NEGOTIATION 단계, 확률 60%"],
                    "alerts": ["분위기 점수 하락 추세"]
                  }
                }
                """;

        when(briefingRestTemplate.postForObject(
                eq(AI_SERVER_URL + "/api/v1/ai/briefing"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseJson);

        BriefingResponse response = briefingClient.generate(TENANT_ID, sampleRequest());

        assertThat(response.keyPoints()).containsExactly("딜 현황: NEGOTIATION 단계, 확률 60%");
        assertThat(response.alerts()).containsExactly("분위기 점수 하락 추세");
    }

    @Test
    @DisplayName("요청 본문에 snake_case 필드명이 포함된다.")
    @SuppressWarnings("unchecked")
    void requestBodyUsesSnakeCase() {
        String responseJson = """
                {"status":200,"message":"success",
                 "data":{"key_points":["포인트1"],"alerts":[]}}
                """;
        when(briefingRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseJson);

        briefingClient.generate(TENANT_ID, sampleRequest());

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(briefingRestTemplate).postForObject(any(String.class), captor.capture(), eq(String.class));

        String body = captor.getValue().getBody();
        assertThat(body).contains("\"activity_id\":");
        assertThat(body).contains("\"account_id\":");
        assertThat(body).contains("\"account_name\":");
        assertThat(body).contains("\"scheduled_at\":");
    }

    @Test
    @DisplayName("X-Tenant-Id 헤더가 요청에 포함된다.")
    @SuppressWarnings("unchecked")
    void tenantHeaderIncluded() {
        String responseJson = """
                {"status":200,"message":"success",
                 "data":{"key_points":["포인트1"],"alerts":[]}}
                """;
        when(briefingRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseJson);

        briefingClient.generate(TENANT_ID, sampleRequest());

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(briefingRestTemplate).postForObject(any(String.class), captor.capture(), eq(String.class));

        assertThat(captor.getValue().getHeaders().getFirst("X-Tenant-Id")).isEqualTo("1");
    }

    @Test
    @DisplayName("FastAPI 호출 실패 시 EXTERNAL_API_FAILED 예외가 발생한다.")
    void throwsOnRestClientException() {
        when(briefingRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        assertThatThrownBy(() -> briefingClient.generate(TENANT_ID, sampleRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);
    }

    @Test
    @DisplayName("응답 본문이 비어있으면 EXTERNAL_API_FAILED 예외가 발생한다.")
    void throwsOnEmptyBody() {
        when(briefingRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn("");

        assertThatThrownBy(() -> briefingClient.generate(TENANT_ID, sampleRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);
    }

    @Test
    @DisplayName("data 필드가 없으면 EXTERNAL_API_FAILED 예외가 발생한다.")
    void throwsWhenDataFieldMissing() {
        when(briefingRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{\"status\":500,\"message\":\"LLM_ERROR\",\"data\":null}");

        assertThatThrownBy(() -> briefingClient.generate(TENANT_ID, sampleRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);
    }

    @Test
    @DisplayName("key_points 가 비어있어도 정상 응답으로 처리된다.")
    void emptyKeyPointsIsValid() {
        when(briefingRestTemplate.postForObject(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn("""
                        {"status":200,"message":"success",
                         "data":{"key_points":[],"alerts":[]}}
                        """);

        BriefingResponse response = briefingClient.generate(TENANT_ID, sampleRequest());

        assertThat(response.keyPoints()).isEmpty();
        assertThat(response.alerts()).isEmpty();
    }
}

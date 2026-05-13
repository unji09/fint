package com.ssafy.fint.domain.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NextActionClientTest {

    private static final String AI_SERVER_URL = "http://ai-server:8000";
    private static final Long TENANT_ID = 1L;
    private static final Long ACCOUNT_ID = 7L;

    @Mock private RestTemplate aiRestTemplate;

    private NextActionClient nextActionClient;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        nextActionClient = new NextActionClient(aiRestTemplate, objectMapper, AI_SERVER_URL);
    }

    @Test
    @DisplayName("정상 응답 시 snake_case JSON 이 NextActionAiResponse 로 역직렬화된다.")
    void parseSnakeCaseResponse() {
        String responseJson = """
                {
                  "title": "클라우드 전환 제안",
                  "description": "비용 절감 가능",
                  "category": "전략",
                  "success_probability": 89,
                  "sources": { "news": [], "dart": [], "crm": [] },
                  "recommended_script": "멘트 내용",
                  "risk": "리스크 내용",
                  "pipeline_stage_id": 10
                }
                """;

        when(aiRestTemplate.postForEntity(
                eq(AI_SERVER_URL + "/api/v1/ai/next-actions"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseJson, HttpStatus.OK));

        NextActionAiResponse response = nextActionClient.generate(TENANT_ID, ACCOUNT_ID, null);

        assertThat(response.title()).isEqualTo("클라우드 전환 제안");
        assertThat(response.description()).isEqualTo("비용 절감 가능");
        assertThat(response.category()).isEqualTo("전략");
        assertThat(response.successProbability()).isEqualTo(89);
        assertThat(response.recommendedScript()).isEqualTo("멘트 내용");
        assertThat(response.risk()).isEqualTo("리스크 내용");
        assertThat(response.pipelineStageId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("요청 본문에 account_id 가 포함되고 context 가 null 이면 생략된다.")
    @SuppressWarnings("unchecked")
    void requestBodyContainsAccountId() {
        String responseJson = """
                { "title": "t", "description": "d", "category": "c",
                  "success_probability": 1, "sources": {}, "recommended_script": "s",
                  "risk": "r", "pipeline_stage_id": 10 }
                """;

        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseJson, HttpStatus.OK));

        nextActionClient.generate(TENANT_ID, ACCOUNT_ID, null);

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(aiRestTemplate).postForEntity(any(String.class), captor.capture(), eq(String.class));

        String body = captor.getValue().getBody();
        assertThat(body).contains("\"account_id\":");
        assertThat(body).contains("7");
        assertThat(body).doesNotContain("\"context\"");
    }

    @Test
    @DisplayName("X-Tenant-Id 헤더가 요청에 포함된다.")
    @SuppressWarnings("unchecked")
    void tenantHeaderIncluded() {
        String responseJson = """
                { "title": "t", "description": "d", "category": "c",
                  "success_probability": 1, "sources": {}, "recommended_script": "s",
                  "risk": "r", "pipeline_stage_id": 10 }
                """;

        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseJson, HttpStatus.OK));

        nextActionClient.generate(TENANT_ID, ACCOUNT_ID, "추가 맥락");

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(aiRestTemplate).postForEntity(any(String.class), captor.capture(), eq(String.class));

        assertThat(captor.getValue().getHeaders().getFirst("X-Tenant-Id")).isEqualTo("1");
    }

    @Test
    @DisplayName("FastAPI 호출 실패 시 EXTERNAL_API_FAILED 예외가 발생한다.")
    void externalApiFailedOnRestClientException() {
        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        assertThatThrownBy(() -> nextActionClient.generate(TENANT_ID, ACCOUNT_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);
    }

    @Test
    @DisplayName("응답 본문이 비어있으면 EXTERNAL_API_FAILED 예외가 발생한다.")
    void externalApiFailedOnEmptyBody() {
        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        assertThatThrownBy(() -> nextActionClient.generate(TENANT_ID, ACCOUNT_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);
    }

    @Test
    @DisplayName("필수 필드(title)가 null 이면 EXTERNAL_API_FAILED 예외가 발생한다.")
    void externalApiFailedOnMissingRequiredField() {
        String responseJson = """
                { "description": "d", "category": "c",
                  "success_probability": 1, "sources": {}, "recommended_script": "s",
                  "risk": "r", "pipeline_stage_id": 10 }
                """;

        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseJson, HttpStatus.OK));

        assertThatThrownBy(() -> nextActionClient.generate(TENANT_ID, ACCOUNT_ID, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);
    }
}

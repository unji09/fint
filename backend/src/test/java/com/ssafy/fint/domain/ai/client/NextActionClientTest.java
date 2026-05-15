package com.ssafy.fint.domain.ai.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.ssafy.fint.domain.ai.dto.NextActionCreateRequest;
import com.ssafy.fint.domain.ai.entity.TriggerType;
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

import java.util.List;

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
    @DisplayName("정상 응답 시 JSON 배열이 List<NextActionAiResponse> 로 역직렬화된다.")
    void parseArrayResponse() {
        String responseJson = """
                [
                  {
                    "action": "클라우드 전환 제안",
                    "reason": "비용 절감 가능",
                    "category": "COST_REDUCTION",
                    "related_type": "ACCOUNT",
                    "importance_score": 87.5,
                    "success_probability": 89,
                    "sources": { "news": [], "dart": [], "crm": [] },
                    "recommended_script": "멘트 내용",
                    "pipeline_stage_id": 10
                  },
                  {
                    "action": "EB 식별",
                    "reason": "EB 미참여",
                    "category": "Qualification",
                    "related_type": "ACCOUNT",
                    "importance_score": 72.0,
                    "success_probability": 72,
                    "sources": { "news": [], "dart": [], "crm": [] },
                    "recommended_script": "Champion 에게 요청",
                    "pipeline_stage_id": 2
                  }
                ]
                """;

        when(aiRestTemplate.postForEntity(
                eq(AI_SERVER_URL + "/api/v1/ai/next-actions"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseJson, HttpStatus.OK));

        NextActionCreateRequest request = new NextActionCreateRequest(
                ACCOUNT_ID, TriggerType.EXTERNAL_SIGNAL_UPDATED,
                List.of(101L), List.of(55L), null, null);
        List<NextActionAiResponse> responses = nextActionClient.generate(TENANT_ID, request);

        assertThat(responses).hasSize(2);

        NextActionAiResponse first = responses.get(0);
        assertThat(first.action()).isEqualTo("클라우드 전환 제안");
        assertThat(first.reason()).isEqualTo("비용 절감 가능");
        assertThat(first.category()).isEqualTo("COST_REDUCTION");
        assertThat(first.relatedType()).isEqualTo("ACCOUNT");
        assertThat(first.importanceScore()).isEqualTo(87.5);
        assertThat(first.successProbability()).isEqualTo(89);
        assertThat(first.recommendedScript()).isEqualTo("멘트 내용");
        assertThat(first.pipelineStageId()).isEqualTo(10L);

        NextActionAiResponse second = responses.get(1);
        assertThat(second.action()).isEqualTo("EB 식별");
        assertThat(second.pipelineStageId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("요청 본문에 trigger_type, news_article_ids 등이 포함된다.")
    @SuppressWarnings("unchecked")
    void requestBodyContainsNewFields() {
        String responseJson = """
                [{ "action": "t", "reason": "d", "category": "c",
                   "related_type": "ACCOUNT", "importance_score": 50.0,
                   "success_probability": 1, "sources": {},
                   "recommended_script": "s", "pipeline_stage_id": 10 }]
                """;

        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseJson, HttpStatus.OK));

        NextActionCreateRequest request = new NextActionCreateRequest(
                ACCOUNT_ID, TriggerType.NEWS_UPDATED,
                List.of(101L, 102L), null, null, null);
        nextActionClient.generate(TENANT_ID, request);

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(aiRestTemplate).postForEntity(any(String.class), captor.capture(), eq(String.class));

        String body = captor.getValue().getBody();
        assertThat(body).contains("\"account_id\":");
        assertThat(body).contains("\"trigger_type\":");
        assertThat(body).contains("NEWS_UPDATED");
        assertThat(body).contains("\"news_article_ids\":");
        assertThat(body).doesNotContain("\"context\"");
        assertThat(body).doesNotContain("\"dart_disclosure_ids\"");
    }

    @Test
    @DisplayName("X-Tenant-Id 헤더가 요청에 포함된다.")
    @SuppressWarnings("unchecked")
    void tenantHeaderIncluded() {
        String responseJson = """
                [{ "action": "t", "reason": "d", "category": "c",
                   "related_type": "ACCOUNT", "importance_score": 50.0,
                   "success_probability": 1, "sources": {},
                   "recommended_script": "s", "pipeline_stage_id": 10 }]
                """;

        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseJson, HttpStatus.OK));

        NextActionCreateRequest request = new NextActionCreateRequest(
                ACCOUNT_ID, TriggerType.NEWS_UPDATED,
                null, null, null, "추가 맥락");
        nextActionClient.generate(TENANT_ID, request);

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(aiRestTemplate).postForEntity(any(String.class), captor.capture(), eq(String.class));

        assertThat(captor.getValue().getHeaders().getFirst("X-Tenant-Id")).isEqualTo("1");
    }

    @Test
    @DisplayName("FastAPI 호출 실패 시 EXTERNAL_API_FAILED 예외가 발생한다.")
    void externalApiFailedOnRestClientException() {
        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        NextActionCreateRequest request = new NextActionCreateRequest(
                ACCOUNT_ID, TriggerType.NEWS_UPDATED, null, null, null, null);

        assertThatThrownBy(() -> nextActionClient.generate(TENANT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);
    }

    @Test
    @DisplayName("응답 본문이 비어있으면 EXTERNAL_API_FAILED 예외가 발생한다.")
    void externalApiFailedOnEmptyBody() {
        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        NextActionCreateRequest request = new NextActionCreateRequest(
                ACCOUNT_ID, TriggerType.NEWS_UPDATED, null, null, null, null);

        assertThatThrownBy(() -> nextActionClient.generate(TENANT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);
    }

    @Test
    @DisplayName("빈 배열 응답이면 EXTERNAL_API_FAILED 예외가 발생한다.")
    void externalApiFailedOnEmptyArray() {
        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("[]", HttpStatus.OK));

        NextActionCreateRequest request = new NextActionCreateRequest(
                ACCOUNT_ID, TriggerType.NEWS_UPDATED, null, null, null, null);

        assertThatThrownBy(() -> nextActionClient.generate(TENANT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);
    }

    @Test
    @DisplayName("배열 내 항목에 필수 필드(action)가 null 이면 EXTERNAL_API_FAILED 예외가 발생한다.")
    void externalApiFailedOnMissingRequiredField() {
        String responseJson = """
                [{ "reason": "d", "category": "c",
                   "related_type": "ACCOUNT", "importance_score": 50.0,
                   "success_probability": 1, "sources": {},
                   "recommended_script": "s", "pipeline_stage_id": 10 }]
                """;

        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(responseJson, HttpStatus.OK));

        NextActionCreateRequest request = new NextActionCreateRequest(
                ACCOUNT_ID, TriggerType.NEWS_UPDATED, null, null, null, null);

        assertThatThrownBy(() -> nextActionClient.generate(TENANT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);
    }
}

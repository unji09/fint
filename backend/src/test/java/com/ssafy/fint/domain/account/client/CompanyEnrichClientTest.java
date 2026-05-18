package com.ssafy.fint.domain.account.client;

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
class CompanyEnrichClientTest {

    private static final String AI_SERVER_URL = "http://ai-server:8000";
    private static final Long TENANT_ID = 1L;

    @Mock private RestTemplate aiRestTemplate;

    private CompanyEnrichClient client;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @BeforeEach
    void setUp() {
        client = new CompanyEnrichClient(aiRestTemplate, objectMapper, AI_SERVER_URL);
    }

    @Test
    @DisplayName("matched=true 응답 시 company 정보가 정상 역직렬화된다.")
    void matchedTrue_returnsSingleCompany() {
        String json = """
                {
                  "status": 200,
                  "message": "success",
                  "data": {
                    "matched": true,
                    "company": {
                      "corp_code": "00126186",
                      "corp_name": "삼성에스디에스(주)",
                      "biz_no": "1108128774",
                      "industry_code": "62021",
                      "ceo_name": "이준희",
                      "address": "서울특별시 송파구",
                      "phone": "02-6155-3114",
                      "established_date": "19850501"
                    },
                    "candidates": []
                  }
                }
                """;

        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

        CompanyEnrichApiResponse.Data result = client.enrich(TENANT_ID, "삼성SDS");

        assertThat(result.matched()).isTrue();
        assertThat(result.company()).isNotNull();
        assertThat(result.company().corpCode()).isEqualTo("00126186");
        assertThat(result.company().bizNo()).isEqualTo("1108128774");
        assertThat(result.company().industryCode()).isEqualTo("62021");
        assertThat(result.company().ceoName()).isEqualTo("이준희");
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    @DisplayName("matched=false + candidates 응답 시 후보 목록이 역직렬화된다.")
    void matchedFalse_withCandidates() {
        String json = """
                {
                  "status": 200,
                  "message": "success",
                  "data": {
                    "matched": false,
                    "company": null,
                    "candidates": [
                      {"corp_code": "00164742", "corp_name": "현대자동차", "biz_no": "1018109147", "industry_code": "30121", "ceo_name": "정의선", "address": "서울", "phone": "02-1234", "established_date": "19670601"},
                      {"corp_code": "00164779", "corp_name": "현대건설", "biz_no": "1018122092", "industry_code": "41220", "ceo_name": "윤영준", "address": "서울", "phone": "02-5678", "established_date": "19500110"}
                    ]
                  }
                }
                """;

        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

        CompanyEnrichApiResponse.Data result = client.enrich(TENANT_ID, "현대자동");

        assertThat(result.matched()).isFalse();
        assertThat(result.company()).isNull();
        assertThat(result.candidates()).hasSize(2);
        assertThat(result.candidates().get(0).corpName()).isEqualTo("현대자동차");
        assertThat(result.candidates().get(1).corpName()).isEqualTo("현대건설");
    }

    @Test
    @DisplayName("matched=false + 빈 candidates 응답.")
    void matchedFalse_noCandidates() {
        String json = """
                {
                  "status": 200,
                  "message": "success",
                  "data": {
                    "matched": false,
                    "company": null,
                    "candidates": []
                  }
                }
                """;

        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

        CompanyEnrichApiResponse.Data result = client.enrich(TENANT_ID, "없는회사");

        assertThat(result.matched()).isFalse();
        assertThat(result.company()).isNull();
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    @DisplayName("요청에 X-Tenant-Id 헤더가 포함된다.")
    @SuppressWarnings("unchecked")
    void tenantHeaderIncluded() {
        String json = """
                {"status":200,"message":"success","data":{"matched":false,"company":null,"candidates":[]}}
                """;

        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

        client.enrich(TENANT_ID, "테스트");

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(aiRestTemplate).postForEntity(any(String.class), captor.capture(), eq(String.class));

        assertThat(captor.getValue().getHeaders().getFirst("X-Tenant-Id")).isEqualTo("1");
    }

    @Test
    @DisplayName("요청 본문에 company_name이 포함된다.")
    @SuppressWarnings("unchecked")
    void requestBodyContainsCompanyName() {
        String json = """
                {"status":200,"message":"success","data":{"matched":false,"company":null,"candidates":[]}}
                """;

        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

        client.enrich(TENANT_ID, "삼성SDS");

        ArgumentCaptor<HttpEntity<String>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(aiRestTemplate).postForEntity(any(String.class), captor.capture(), eq(String.class));

        assertThat(captor.getValue().getBody()).contains("\"company_name\"");
        assertThat(captor.getValue().getBody()).contains("삼성SDS");
    }

    @Test
    @DisplayName("RestClientException 시 EXTERNAL_API_FAILED 예외 발생.")
    void externalApiFailedOnRestClientException() {
        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        assertThatThrownBy(() -> client.enrich(TENANT_ID, "삼성SDS"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);
    }

    @Test
    @DisplayName("빈 응답 본문 시 EXTERNAL_API_FAILED 예외 발생.")
    void externalApiFailedOnEmptyBody() {
        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>("", HttpStatus.OK));

        assertThatThrownBy(() -> client.enrich(TENANT_ID, "삼성SDS"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);
    }

    @Test
    @DisplayName("data가 null이면 EXTERNAL_API_FAILED 예외 발생.")
    void externalApiFailedOnNullData() {
        String json = """
                {"status":200,"message":"success","data":null}
                """;

        when(aiRestTemplate.postForEntity(any(String.class), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(json, HttpStatus.OK));

        assertThatThrownBy(() -> client.enrich(TENANT_ID, "삼성SDS"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.EXTERNAL_API_FAILED);
    }
}

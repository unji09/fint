package com.ssafy.fint.global.config;

import com.ssafy.fint.config.TestcontainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfig.class)
@TestPropertySource(properties = {
    "server.forward-headers-strategy=native",
    "cors.allowed-origins=https://k14a301.p.ssafy.io"
})
class SecurityConfigProxyTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private static final String LOGIN_BODY =
            "{\"companyCode\":\"test\",\"empNo\":\"test\",\"password\":\"test\"}";

    @Test
    @DisplayName("permitAll 엔드포인트는 프록시 헤더 없이 접근 가능 (403 아님)")
    void loginWithoutProxyHeaders() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY))
                .andExpect(status().is(not(403)));
    }

    @Test
    @DisplayName("permitAll 엔드포인트는 X-Forwarded-Proto: https 에서도 접근 가능 (403 아님)")
    void loginWithForwardedProto() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY)
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-For", "172.18.0.1"))
                .andExpect(status().is(not(403)));
    }

    @Test
    @DisplayName("permitAll 엔드포인트는 Origin + 프록시 헤더 조합에서도 접근 가능 (403 아님)")
    void loginWithOriginAndForwardedHeaders() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY)
                        .header("Origin", "https://k14a301.p.ssafy.io")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-For", "172.18.0.1"))
                .andExpect(status().is(not(403)));
    }

    @Test
    @DisplayName("인증 필요 엔드포인트는 토큰 없으면 401 또는 403 (permitAll과 구분 확인)")
    void protectedEndpointWithoutToken() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-For", "172.18.0.1"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("SSE 스트림 엔드포인트는 토큰 없으면 401 또는 403")
    void sseStreamWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/dashboards/queries/test-trace-id/stream")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().is4xxClientError());
    }
}

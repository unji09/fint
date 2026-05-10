package com.ssafy.fint.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Spring → FastAPI 내부 호출용 RestTemplate.
 * 타임아웃 기본값: connect 3s, read 10s. 긴 LLM 호출은 별도 beans 추가 고려.
 *
 * <p>HTTP 클라이언트로 {@link SimpleClientHttpRequestFactory} 를 명시 사용 — HTTP/1.1 전용.
 * Spring Boot 4 의 RestTemplateBuilder 는 classpath 에 따라 JDK java.net.http.HttpClient 또는
 * Apache HttpClient5 를 자동 선택하는데, 둘 다 HTTP/2 upgrade 를 자동 시도한다.
 * 대상 FastAPI 가 uvicorn h11 (HTTP/2 미지원) 이라 upgrade 시도 시 body framing 이 깨져
 * Content-Length 만 박히고 실제 body 가 0 bytes 로 도착하는 문제 회피.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate aiRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(10))
                .requestFactory(SimpleClientHttpRequestFactory::new)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}

package com.ssafy.fint.global.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Spring → FastAPI 내부 호출용 RestTemplate.
 * <p>
 * Spring Boot 4 부터 기본 자동설정 ObjectMapper 는 Jackson 3({@code tools.jackson.databind.ObjectMapper}) 이지만,
 * 본 프로젝트의 도메인 서비스(DashboardQueryService 등)는
 * Jackson 2({@code com.fasterxml.jackson.databind.ObjectMapper}) 에 의존하므로 명시적으로 등록한다.
 * <p>
 * RestTemplate 의 JSON 메시지 컨버터는 Spring Boot 4 기본값(Jackson 3 기반) 을 그대로 사용한다.
 * Jackson 2 의 {@code @JsonProperty} 어노테이션은 Jackson 3 컨버터에서 무시되므로,
 * snake_case 필드를 보내야 하는 외부 호출에서는 DTO record 가 아닌 {@code Map} 으로 본문을 구성한다.
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    /**
     * uvicorn 등 HTTP/1.1 전용 서버를 호출하기 위한 RequestFactory.
     * <p>
     * Spring Boot 4 의 기본 {@link JdkClientHttpRequestFactory} 는 JDK {@link HttpClient} 를 통해
     * 평문 HTTP 호출에서도 {@code Upgrade: h2c} 헤더로 HTTP/2 업그레이드를 시도한다.
     * uvicorn 은 h2c 를 지원하지 않아 "Invalid HTTP request received" 400 으로 거부하므로,
     * 명시적으로 HTTP/1.1 로 고정한다.
     */
    private JdkClientHttpRequestFactory http11RequestFactory(Duration connectTimeout, Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return factory;
    }

    @Bean
    public RestTemplate aiRestTemplate(RestTemplateBuilder builder) {
        return builder
                .requestFactory(() -> http11RequestFactory(Duration.ofSeconds(3), Duration.ofSeconds(10)))
                .build();
    }

    /**
     * 명함 OCR 전용 RestTemplate.
     * FastAPI 명세: 캐시 미스 + LLM 폴백 시 5~8s 소요. 권장 타임아웃 15s.
     */
    @Bean
    public RestTemplate ocrRestTemplate(RestTemplateBuilder builder) {
        return builder
                .requestFactory(() -> http11RequestFactory(Duration.ofSeconds(3), Duration.ofSeconds(15)))
                .build();
    }

    @Bean
    public RestTemplate signalRestTemplate(RestTemplateBuilder builder) {
        return builder
                .requestFactory(() -> http11RequestFactory(Duration.ofSeconds(5), Duration.ofSeconds(120)))
                .build();
    }
}

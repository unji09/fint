package com.ssafy.fint.global.config;

import com.ssafy.fint.global.common.constant.ApiPath;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String SECURITY_SCHEME_NAME = "JWT";

    @Value("${app.server-url:http://localhost:8080}")
    private String serverUrl;

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .addServersItem(new Server().url(serverUrl).description(activeProfile))
                .info(new Info()
                        .title("F!NT API")
                        .description("F!NT - 지능형 영업관리를 위한 인공지능 B2B CRM API 문서")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components().addSecuritySchemes(
                        SECURITY_SCHEME_NAME,
                        new SecurityScheme()
                                .name("Authorization")
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                ));
    }

    // 도메인별 API 그룹. 도메인 추가될 때마다 여기에 Bean 하나씩 늘린다.
    @Bean
    public GroupedOpenApi authApi() {
        return GroupedOpenApi.builder()
                .group("1. AUTH API")
                .pathsToMatch(ApiPath.V1 + "/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi activityApi() {
        return GroupedOpenApi.builder()
                .group("2. ACTIVITY API")
                .pathsToMatch(ApiPath.V1 + "/activities/**")
                .build();
    }

    @Bean
    public GroupedOpenApi customerApi() {
        return GroupedOpenApi.builder()
                .group("3. CUSTOMER API")
                .pathsToMatch(ApiPath.V1 + "/accounts/**")
                .build();
    }
}

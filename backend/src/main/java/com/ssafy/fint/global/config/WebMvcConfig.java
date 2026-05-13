package com.ssafy.fint.global.config;

import com.ssafy.fint.global.common.constant.ApiPath;
import com.ssafy.fint.global.interceptor.ApiLoggingInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final ApiLoggingInterceptor apiLoggingInterceptor;

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(ApiPath.V1, c ->
                c.isAnnotationPresent(RestController.class)
                        && c.getPackageName().startsWith("com.ssafy.fint"));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiLoggingInterceptor)
                .addPathPatterns("/api/**");
    }
}

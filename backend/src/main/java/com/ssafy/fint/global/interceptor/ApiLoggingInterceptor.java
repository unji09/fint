package com.ssafy.fint.global.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
public class ApiLoggingInterceptor implements HandlerInterceptor {

    private static final String ATTR_START_TIME = "requestStartTime";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());
        log.info("[HTTP >>] {} {}", request.getMethod(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        long start = (long) request.getAttribute(ATTR_START_TIME);
        long elapsed = System.currentTimeMillis() - start;
        log.info("[HTTP <<] {} {} {} {}ms", request.getMethod(), request.getRequestURI(), response.getStatus(), elapsed);
    }
}

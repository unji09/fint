package com.ssafy.fint.global.config;

import com.ssafy.fint.domain.activity.websocket.SttStreamHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class SttStreamWebSocketConfig implements WebSocketConfigurer {

    private final SttStreamHandler sttStreamHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(sttStreamHandler, "/ws/stt/*")
                .setAllowedOriginPatterns("*");
    }

    // Tomcat 기본 바이너리 버퍼 8KB → 512KB 확장
    // 1초 webm/opus 청크가 ~10-15KB 이므로 기본값에서 1009(message too large) 발생
    @Bean
    public ServletServerContainerFactoryBean createWebSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxBinaryMessageBufferSize(512 * 1024);
        container.setMaxTextMessageBufferSize(64 * 1024);
        return container;
    }
}

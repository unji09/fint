package com.ssafy.fint.global.config;

import com.ssafy.fint.domain.activity.websocket.SttStreamHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

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
}

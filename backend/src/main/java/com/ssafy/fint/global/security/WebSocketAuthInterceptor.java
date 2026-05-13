package com.ssafy.fint.global.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String AUTH_HEADER = "Authorization";

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
            return message;
        }

        List<String> authHeaders = accessor.getNativeHeader(AUTH_HEADER);
        if (authHeaders == null || authHeaders.isEmpty()) {
            throw new IllegalArgumentException("WebSocket 연결에 인증 토큰이 필요합니다.");
        }

        String token = jwtTokenProvider.extractFromHeader(authHeaders.getFirst());
        jwtTokenProvider.validate(token);

        Long userId = jwtTokenProvider.getUserId(token);
        Long tenantId = jwtTokenProvider.getTenantId(token);
        String role = jwtTokenProvider.getRole(token);

        CustomUserDetails userDetails = new CustomUserDetails(userId, tenantId, role);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        accessor.setUser(auth);
        log.debug("[WebSocket CONNECT] userId={} tenantId={}", userId, tenantId);
        return message;
    }
}

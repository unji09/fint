package com.ssafy.fint.global.security;

import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenValidMs;
    private final long refreshTokenValidMs;

    public JwtTokenProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.access-expiration}") long accessExpiration,
        @Value("${jwt.refresh-expiration}") long refreshExpiration
    ) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64URL.decode(secret));
        this.accessTokenValidMs = accessExpiration;
        this.refreshTokenValidMs = refreshExpiration;
    }

    // ── 토큰 생성 ─────────────────────────────────────────────────

    public String createAccessToken(Long userId, String role, Long tenantId) {
        Date now = new Date();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .claim("role", role)
            .claim("tenantId", tenantId)
            .issuedAt(now)
            .expiration(new Date(now.getTime() + accessTokenValidMs))
            .signWith(key)
            .compact();
    }

    public String createRefreshToken(Long userId) {
        Date now = new Date();
        return Jwts.builder()
            .subject(String.valueOf(userId))
            .issuedAt(now)
            .expiration(new Date(now.getTime() + refreshTokenValidMs))
            .signWith(key)
            .compact();
    }

    public String extractFromHeader(String authHeader) {
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        return authHeader.substring(7);
    }

    // ── 토큰 파싱 ─────────────────────────────────────────────────

    public Long getUserId(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    public String getRole(String token) {
        return getClaims(token).get("role", String.class);
    }

    public Long getTenantId(String token) {
        return getClaims(token).get("tenantId", Long.class);
    }

    /** 블랙리스트 TTL 계산용 */
    public long getExpiration(String token) {
        return Math.max(0,
            getClaims(token).getExpiration().getTime() - System.currentTimeMillis());
    }

    // ── 토큰 검증 ─────────────────────────────────────────────────

    public void validate(String token) {
        try {
            getClaims(token);
        } catch (ExpiredJwtException e) {
            throw new BusinessException(AuthErrorCode.EXPIRED_TOKEN);
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
    }

    // ── Private ───────────────────────────────────────────────────

    private Claims getClaims(String token) {
        return Jwts.parser()
            .verifyWith(key)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}

package com.ssafy.fint.domain.auth.service;

import com.ssafy.fint.domain.auth.dto.*;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.tenant.repository.TenantRepository;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private static final String REFRESH_PREFIX  = "refresh:";
    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, String> redisTemplate;

    // ── 로그인 ────────────────────────────────────────────────────

    public LoginResponse login(LoginRequest request) {

        // 1. 회사 코드 → 테넌트 조회
        Tenant tenant = tenantRepository
            .findByCompanyCodeAndIsDeletedFalse(request.companyCode())
            .orElseThrow(() -> new BusinessException(AuthErrorCode.COMPANY_NOT_FOUND));

        // 2. 테넌트 범위 내 사번 → 사용자 조회 (테넌트 격리)
        User user = userRepository
            .findByTenant_TenantIdAndEmpNoAndIsDeletedFalse(tenant.getTenantId(), request.empNo())
            .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_CREDENTIALS));

        // 3. 비밀번호 검증
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        // 4. 토큰 발급
        String accessToken  = jwtTokenProvider.createAccessToken(
            user.getUserId(), user.getRole().name(), tenant.getTenantId());
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getUserId());

        // 5. Redis 에 refreshToken 저장
        redisTemplate.opsForValue().set(
            REFRESH_PREFIX + user.getUserId(),
            refreshToken,
            7, TimeUnit.DAYS
        );

        log.info("[Login] userId={} tenantId={}", user.getUserId(), tenant.getTenantId());

        return LoginResponse.of(accessToken, refreshToken, user);
    }

    // ── 로그아웃 ──────────────────────────────────────────────────

    public void logout(String accessToken) {

        jwtTokenProvider.validate(accessToken);

        Long userId = jwtTokenProvider.getUserId(accessToken);

        // refreshToken 삭제
        redisTemplate.delete(REFRESH_PREFIX + userId);

        // accessToken 블랙리스트 등록
        long remainMs = jwtTokenProvider.getExpiration(accessToken);
        if (remainMs > 0) {
            redisTemplate.opsForValue().set(
                BLACKLIST_PREFIX + accessToken,
                "logout",
                remainMs, TimeUnit.MILLISECONDS
            );
        }

        log.info("[Logout] userId={}", userId);
    }

    // ── 토큰 재발급 ───────────────────────────────────────────────

    public ReissueResponse reissue(ReissueRequest request) {

        String refreshToken = request.refreshToken();

        // 1. 리프레시 토큰 검증
        jwtTokenProvider.validate(refreshToken);

        Long userId = jwtTokenProvider.getUserId(refreshToken);

        // 2. Redis 저장값과 일치 확인
        String stored = redisTemplate.opsForValue().get(REFRESH_PREFIX + userId);
        if (stored == null || !stored.equals(refreshToken)) {
            throw new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 3. 사용자 조회 (role, tenantId 최신값 반영)
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));

        // 4. 새 accessToken 발급
        String newAccessToken = jwtTokenProvider.createAccessToken(
            user.getUserId(), user.getRole().name(), user.getTenant().getTenantId());

        log.info("[Reissue] userId={}", userId);

        return ReissueResponse.of(newAccessToken);
    }
}

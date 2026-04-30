package com.ssafy.fint.domain.auth.controller;

import com.ssafy.fint.domain.auth.dto.*;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.domain.auth.service.AuthService;
import com.ssafy.fint.global.ApiResponse;
import com.ssafy.fint.global.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String BEARER_PREFIX = "Bearer ";
    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
        @Valid @RequestBody LoginRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(authService.login(request)));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
        @RequestHeader("Authorization") String authHeader) {

        authService.logout(extractToken(authHeader));
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<ReissueResponse>> reissue(
        @Valid @RequestBody ReissueRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(authService.reissue(request)));
    }

    private String extractToken(String authHeader) {
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        return authHeader.substring(BEARER_PREFIX.length());
    }
}

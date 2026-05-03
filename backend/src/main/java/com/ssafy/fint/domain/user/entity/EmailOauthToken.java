package com.ssafy.fint.domain.user.entity;

import com.ssafy.fint.global.common.entity.BaseUpdatableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 이메일 OAuth 토큰.
 * 사용자별로 외부 이메일 제공자(Gmail 등)의 access/refresh 토큰을 암호화하여 저장한다.
 */
@Entity
@Table(name = "email_oauth_tokens")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailOauthToken extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "email_oauth_id")
    private Long emailOauthId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "email_address", nullable = false, length = 200)
    private String emailAddress;

    @Column(name = "access_token_enc", nullable = false, columnDefinition = "TEXT")
    private String accessTokenEnc;

    @Column(name = "refresh_token_enc", nullable = false, columnDefinition = "TEXT")
    private String refreshTokenEnc;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "last_sync_at")
    private OffsetDateTime lastSyncAt;

    @Builder
    private EmailOauthToken(
            User user,
            String provider,
            String emailAddress,
            String accessTokenEnc,
            String refreshTokenEnc,
            OffsetDateTime expiresAt
    ) {
        this.user = user;
        this.provider = provider;
        this.emailAddress = emailAddress;
        this.accessTokenEnc = accessTokenEnc;
        this.refreshTokenEnc = refreshTokenEnc;
        this.expiresAt = expiresAt;
    }

    public void rotateTokens(String accessTokenEnc, String refreshTokenEnc, OffsetDateTime expiresAt) {
        this.accessTokenEnc = accessTokenEnc;
        this.refreshTokenEnc = refreshTokenEnc;
        this.expiresAt = expiresAt;
    }

    public void markSynced(OffsetDateTime syncedAt) {
        this.lastSyncAt = syncedAt;
    }
}

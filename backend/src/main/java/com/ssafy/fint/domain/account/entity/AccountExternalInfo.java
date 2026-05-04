package com.ssafy.fint.domain.account.entity;

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
 * 고객사 외부 정보(뉴스, DART 공시 등).
 * 외부에서 수집된 시그널성 정보. 베이스 엔티티 상속 안 함
 * (created_at 컬럼이 없고 occurred_at 만 보유 — 외부 발생 시점).
 */
@Entity
@Table(name = "account_external_info")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountExternalInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_external_info_id")
    private Long accountExternalInfoId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "source", nullable = false, length = 20)
    private String source;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "url", length = 2000)
    private String url;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @Builder
    private AccountExternalInfo(
            Account account,
            String source,
            String title,
            String content,
            String url,
            OffsetDateTime occurredAt
    ) {
        this.account = account;
        this.source = source;
        this.title = title;
        this.content = content;
        this.url = url;
        this.occurredAt = occurredAt;
    }
}

package com.ssafy.fint.domain.deal.entity;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.tenant.entity.Team;
import com.ssafy.fint.global.common.entity.BaseSoftDeletableEntity;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 영업건(Deal).
 * 고객사를 대상으로 하는 개별 영업 기회. won/lost 시점에 결과 기록.
 */
@Entity
@Table(name = "deals")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deal extends BaseSoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "deal_id")
    private Long dealId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "expected_close")
    private LocalDate expectedClose;

    @Column(name = "amount", precision = 15, scale = 2)
    private BigDecimal amount;

    @Column(name = "probability")
    private Short probability;

    @Column(name = "won_at")
    private OffsetDateTime wonAt;

    @Column(name = "lost_at")
    private OffsetDateTime lostAt;

    @Column(name = "lost_reason", columnDefinition = "TEXT")
    private String lostReason;

    @Column(name = "current_pipeline", length = 50)
    private String currentPipeline;

    @Builder
    private Deal(
            Account account,
            Team team,
            String title,
            LocalDate expectedClose,
            BigDecimal amount,
            Short probability,
            String currentPipeline
    ) {
        this.account = account;
        this.team = team;
        this.title = title;
        this.expectedClose = expectedClose;
        this.amount = amount;
        this.probability = probability;
        this.currentPipeline = currentPipeline;
    }

    public void changeTitle(String title) {
        this.title = title;
    }

    public void changeAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public void changeProbability(Short probability) {
        this.probability = probability;
    }

    public void changeExpectedClose(LocalDate expectedClose) {
        this.expectedClose = expectedClose;
    }

    public void changeTeam(Team team) {
        this.team = team;
    }

    public void moveToStage(String stageName) {
        this.currentPipeline = stageName;
    }

    public void markWon(OffsetDateTime wonAt) {
        this.wonAt = wonAt;
        this.lostAt = null;
        this.lostReason = null;
    }

    public void markLost(OffsetDateTime lostAt, String reason) {
        this.lostAt = lostAt;
        this.lostReason = reason;
        this.wonAt = null;
    }
}

package com.ssafy.fint.domain.account.entity;

import com.ssafy.fint.global.common.entity.BaseEntity;
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

/**
 * 고객사 온도 이력(append-only).
 * 시점별 온도 + 변경 사유 기록.
 */
@Entity
@Table(name = "temperature_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemperatureHistory extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "temperature_history_id")
    private Long temperatureHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "temperature", nullable = false)
    private int temperature;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Builder
    private TemperatureHistory(Account account, int temperature, String reason) {
        this.account = account;
        this.temperature = temperature;
        this.reason = reason;
    }
}

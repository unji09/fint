package com.ssafy.fint.domain.account.entity;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

/**
 * 고객사 분위기(날씨) 이력 (append-only).
 * 시점별 mood + 변경 사유 기록. 테이블명은 호환 위해 temperature_history 그대로 유지.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "mood", nullable = false, length = 20)
    private Mood mood;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Column(name = "mood_score", nullable = false)
    private Integer moodScore;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_signals", columnDefinition = "jsonb")
    private List<String> keySignals;

    @Builder
    private TemperatureHistory(Account account, Mood mood, String reason,
                               Activity activity, Integer moodScore, List<String> keySignals) {
        this.account = account;
        this.mood = mood;
        this.reason = reason;
        this.activity = activity;
        this.moodScore = moodScore;
        this.keySignals = keySignals;
    }
}

package com.ssafy.fint.domain.mood.entity;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.mood.MoodType;
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

import java.time.OffsetDateTime;
import java.util.List;

@Entity
@Table(name = "temperature_history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TemperatureHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "temperature_history_id")
    private Long temperatureHistoryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "activity_id")
    private Activity activity;

    @Enumerated(EnumType.STRING)
    @Column(name = "mood", nullable = false, length = 20)
    private MoodType mood;

    @Column(name = "mood_score", nullable = false)
    private Integer moodScore;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_signals", columnDefinition = "jsonb")
    private List<String> keySignals;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Builder
    private TemperatureHistory(
        Account account,
        Activity activity,
        MoodType mood,
        Integer moodScore,
        String reason,
        List<String> keySignals
    ) {
        this.account = account;
        this.activity = activity;
        this.mood = mood;
        this.moodScore = moodScore;
        this.reason = reason;
        this.keySignals = keySignals;
        this.createdAt = OffsetDateTime.now();
    }
}

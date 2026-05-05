package com.ssafy.fint.domain.activity.entity;

import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.global.common.entity.BaseUpdatableEntity;
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
import java.util.Map;

@Entity
@Table(name = "activities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Activity extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "activity_id")
    private Long activityId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "deal_id")
    private Deal deal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_stage_id")
    private PipelineStage pipelineStage;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ActivityType type;

    @Column(name = "device_event_id", length = 200)
    private String deviceEventId;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attendees", columnDefinition = "jsonb")
    private List<Map<String, Object>> attendees;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transcript", columnDefinition = "jsonb")
    private Map<String, Object> transcript;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "summary", columnDefinition = "jsonb")
    private Map<String, Object> summary;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @Enumerated(EnumType.STRING)
    @Column(name = "stt_status", nullable = false, length = 20)
    private SttStatus sttStatus;

    @Builder
    private Activity(
            User user,
            Deal deal,
            PipelineStage pipelineStage,
            ActivityType type,
            String deviceEventId,
            String title,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            List<Map<String, Object>> attendees,
            String memo
    ) {
        this.user = user;
        this.deal = deal;
        this.pipelineStage = pipelineStage;
        this.type = type;
        this.deviceEventId = deviceEventId;
        this.title = title;
        this.startAt = startAt;
        this.endAt = endAt;
        this.attendees = attendees;
        this.memo = memo;
        this.sttStatus = SttStatus.PENDING;
    }

    public void changeSttStatus(SttStatus sttStatus) {
        this.sttStatus = sttStatus;
    }

    public void changeTitle(String title) {
        this.title = title;
    }

    public void changeSchedule(OffsetDateTime startAt, OffsetDateTime endAt) {
        this.startAt = startAt;
        this.endAt = endAt;
    }

    public void changeAttendees(List<Map<String, Object>> attendees) {
        this.attendees = attendees;
    }

    public void changeDeal(Deal deal) {
        this.deal = deal;
    }

    public void changePipelineStage(PipelineStage pipelineStage) {
        this.pipelineStage = pipelineStage;
    }

    public void linkToExternalEvent(String deviceEventId) {
        this.deviceEventId = deviceEventId;
    }

    public void unlinkFromExternalEvent() {
        this.deviceEventId = null;
    }

    public void updateTranscript(Map<String, Object> transcript) {
        this.transcript = transcript;
    }

    public void updateSummary(Map<String, Object> summary) {
        this.summary = summary;
    }

    public void changeMemo(String memo) {
        this.memo = memo;
    }
}

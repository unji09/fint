package com.ssafy.fint.domain.activity.entity;

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

import java.util.Map;

@Entity
@Table(name = "recordings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Recording extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "recording_id")
    private Long recordingId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "activity_id", nullable = false)
    private Activity activity;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "file_key", nullable = false, length = 200)
    private String fileKey;

    @Column(name = "title", nullable = false, length = 300)
    private String title;

    @Column(name = "duration", nullable = false)
    private int duration;

    @Enumerated(EnumType.STRING)
    @Column(name = "stt_status", nullable = false, length = 20)
    private SttStatus sttStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "transcript", columnDefinition = "jsonb")
    private Map<String, Object> transcript;

    @Builder
    private Recording(Activity activity, Long tenantId, String fileKey, String title, int duration) {
        this.activity = activity;
        this.tenantId = tenantId;
        this.fileKey = fileKey;
        this.title = title;
        this.duration = duration;
        this.sttStatus = SttStatus.PROCESSING;
    }

    public void updateTitle(String title) {
        this.title = title;
    }

    public void updateTranscript(Map<String, Object> transcript) {
        this.transcript = transcript;
    }

    public void changeSttStatus(SttStatus sttStatus) {
        this.sttStatus = sttStatus;
    }
}

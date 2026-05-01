package com.ssafy.fint.domain.ai.entity;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.global.common.entity.BaseEntity;
import io.hypersistence.utils.hibernate.type.json.JsonType;
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
import org.hibernate.annotations.Type;

import java.util.Map;

/**
 * AI 제안(Next Action 등).
 * 고객사 기반으로 AI 가 생성한 추천 사항. 알림 영역에서도 활용된다.
 */
@Entity
@Table(name = "ai_suggestions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiSuggestion extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_suggestion_id")
    private Long aiSuggestionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pipeline_stage_id", nullable = false)
    private PipelineStage pipelineStage;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "related_type", nullable = false, length = 50)
    private AiSuggestionRelatedType relatedType;

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Type(JsonType.class)
    @Column(name = "reason", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> reason;

    @Builder
    private AiSuggestion(
            Account account,
            PipelineStage pipelineStage,
            String title,
            String content,
            AiSuggestionRelatedType relatedType,
            Map<String, Object> reason
    ) {
        this.account = account;
        this.pipelineStage = pipelineStage;
        this.title = title;
        this.content = content;
        this.relatedType = relatedType;
        this.isRead = false;
        this.reason = reason;
    }

    public void markAsRead() {
        this.isRead = true;
    }

    public void markAsUnread() {
        this.isRead = false;
    }
}

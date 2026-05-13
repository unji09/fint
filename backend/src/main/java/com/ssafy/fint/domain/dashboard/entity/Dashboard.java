package com.ssafy.fint.domain.dashboard.entity;

import com.ssafy.fint.domain.user.entity.User;
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
 * 대시보드.
 * 사용자별로 생성되며 위젯/쿼리를 포함한다.
 */
@Entity
@Table(name = "dashboards")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dashboard extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "dashboard_id")
    private Long dashboardId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "last_accessed_at")
    private OffsetDateTime lastAccessedAt;

    @Column(name = "thumbnail_key", length = 300)
    private String thumbnailKey;

    @Builder
    private Dashboard(User owner, String title, String thumbnailKey) {
        this.owner = owner;
        this.title = title;
        this.thumbnailKey = thumbnailKey;
    }

    public void changeTitle(String title) {
        this.title = title;
    }

    public void changeThumbnailKey(String thumbnailKey) {
        this.thumbnailKey = thumbnailKey;
    }

    public void touchAccess(OffsetDateTime accessedAt) {
        this.lastAccessedAt = accessedAt;
    }
}

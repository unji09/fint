package com.ssafy.fint.domain.file.entity;

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

/**
 * 업로드 파일 메타데이터.
 * S3 객체 자체는 별도 저장. 본 엔티티는 업로더와 파일 메타(원본명/MIME/크기)만 관리.
 */
@Entity
@Table(name = "files")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class File extends BaseUpdatableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "file_id")
    private Long fileId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "original_name", length = 300)
    private String originalName;

    @Column(name = "mime", nullable = false, length = 100)
    private String mime;

    @Column(name = "size", nullable = false)
    private long size;

    @Builder
    private File(User user, String originalName, String mime, long size) {
        this.user = user;
        this.originalName = originalName;
        this.mime = mime;
        this.size = size;
    }

    public void changeOriginalName(String originalName) {
        this.originalName = originalName;
    }
}

package com.ssafy.fint.domain.signal.entity;

import com.ssafy.fint.global.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Entity
@Table(name = "news_articles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsArticle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "news_article_id")
    private Long newsArticleId;

    @Column(name = "publisher", length = 100)
    private String publisher;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "link", length = 2000)
    private String link;

    @Column(name = "original_link", length = 2000)
    private String originalLink;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "reporter", length = 100)
    private String reporter;

    @Column(name = "article", columnDefinition = "TEXT")
    private String article;

    @Column(name = "content_summary", columnDefinition = "TEXT")
    private String contentSummary;

    @Builder
    private NewsArticle(String publisher, String title, String link, String originalLink,
                        OffsetDateTime publishedAt, String category, String reporter,
                        String article, String contentSummary) {
        this.publisher = publisher;
        this.title = title;
        this.link = link;
        this.originalLink = originalLink;
        this.publishedAt = publishedAt;
        this.category = category;
        this.reporter = reporter;
        this.article = article;
        this.contentSummary = contentSummary;
    }
}

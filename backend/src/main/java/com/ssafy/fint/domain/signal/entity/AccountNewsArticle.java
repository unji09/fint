package com.ssafy.fint.domain.signal.entity;

import com.ssafy.fint.domain.account.entity.Account;
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
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "account_news_articles", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"account_id", "news_article_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountNewsArticle extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_news_article_id")
    private Long accountNewsArticleId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "news_article_id", nullable = false)
    private NewsArticle newsArticle;

    public AccountNewsArticle(Account account, NewsArticle newsArticle) {
        this.account = account;
        this.newsArticle = newsArticle;
    }
}

package com.ssafy.fint.domain.signal.repository;

import com.ssafy.fint.domain.signal.entity.AccountNewsArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;

public interface AccountNewsArticleRepository extends JpaRepository<AccountNewsArticle, Long> {

    boolean existsByAccount_AccountIdAndNewsArticle_NewsArticleId(Long accountId, Long newsArticleId);

    @Query("""
            SELECT a FROM AccountNewsArticle a
            JOIN FETCH a.newsArticle
            WHERE a.account.accountId = :accountId
              AND a.newsArticle.publishedAt >= :since
            ORDER BY a.newsArticle.publishedAt DESC
            """)
    List<AccountNewsArticle> findRecentByAccountId(
            @Param("accountId") Long accountId,
            @Param("since") OffsetDateTime since
    );
}

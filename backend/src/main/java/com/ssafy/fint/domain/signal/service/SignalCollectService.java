package com.ssafy.fint.domain.signal.service;

import com.ssafy.fint.domain.signal.client.SignalCollectClient;
import com.ssafy.fint.domain.signal.client.SignalCollectResponse;
import com.ssafy.fint.domain.signal.client.SignalCollectResponse.DartSection;
import com.ssafy.fint.domain.signal.client.SignalCollectResponse.NewsSection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SignalCollectService {

    private final SignalCollectClient signalCollectClient;
    private final JdbcTemplate jdbcTemplate;

    public SignalCollectService(SignalCollectClient signalCollectClient, JdbcTemplate jdbcTemplate) {
        this.signalCollectClient = signalCollectClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public SignalCollectResult collectAndSave(Long tenantId, List<Long> accountIds) {
        SignalCollectResponse response = signalCollectClient.collect(tenantId, "all", true, accountIds);

        Map<Long, List<Long>> newNewsPerAccount = new HashMap<>();
        Map<Long, List<Long>> mappedNewsPerAccount = new HashMap<>();
        Map<Long, List<Long>> newDartPerAccount = new HashMap<>();
        Map<Long, List<Long>> mappedDartPerAccount = new HashMap<>();

        int newsInserted = processNews(response.news(), newNewsPerAccount, mappedNewsPerAccount);
        int dartInserted = processDart(response.dart(), newDartPerAccount, mappedDartPerAccount);

        if (!response.errors().isEmpty()) {
            log.warn("[SignalCollect] errors from FastAPI: {}", response.errors());
        }

        log.info("[SignalCollect] completed. totalAccounts={} newsInserted={} dartInserted={} errors={}",
                response.totalAccounts(), newsInserted, dartInserted, response.errors().size());

        return new SignalCollectResult(
                response.totalAccounts(), newsInserted, dartInserted,
                response.errors(),
                newNewsPerAccount, mappedNewsPerAccount,
                newDartPerAccount, mappedDartPerAccount
        );
    }

    private int processNews(NewsSection news,
                            Map<Long, List<Long>> newPerAccount,
                            Map<Long, List<Long>> mappedPerAccount) {
        int count = 0;

        for (NewsSection.NewArticle article : news.newArticles()) {
            Long articleId = insertNewsArticle(article);
            if (articleId != null) {
                linkNewsToAccounts(articleId, article.accountIds());
                for (Long accountId : article.accountIds()) {
                    newPerAccount.computeIfAbsent(accountId, k -> new ArrayList<>()).add(articleId);
                }
                count++;
            }
        }

        for (NewsSection.ExistingLink existing : news.existingLinks()) {
            Long articleId = findNewsArticleIdByLink(existing.link());
            if (articleId != null) {
                linkNewsToAccounts(articleId, existing.accountIds());
                for (Long accountId : existing.accountIds()) {
                    mappedPerAccount.computeIfAbsent(accountId, k -> new ArrayList<>()).add(articleId);
                }
            }
        }

        return count;
    }

    private int processDart(DartSection dart,
                            Map<Long, List<Long>> newPerAccount,
                            Map<Long, List<Long>> mappedPerAccount) {
        int count = 0;

        for (DartSection.NewDisclosure disclosure : dart.newDisclosures()) {
            Long disclosureId = insertDartDisclosure(disclosure);
            if (disclosureId != null) {
                linkDartToAccounts(disclosureId, disclosure.accountIds());
                for (Long accountId : disclosure.accountIds()) {
                    newPerAccount.computeIfAbsent(accountId, k -> new ArrayList<>()).add(disclosureId);
                }
                count++;
            }
        }

        for (DartSection.ExistingRceptNo existing : dart.existingRceptNos()) {
            Long disclosureId = findDartDisclosureIdByRceptNo(existing.rceptNo());
            if (disclosureId != null) {
                linkDartToAccounts(disclosureId, existing.accountIds());
                for (Long accountId : existing.accountIds()) {
                    mappedPerAccount.computeIfAbsent(accountId, k -> new ArrayList<>()).add(disclosureId);
                }
            }
        }

        return count;
    }

    private Long insertNewsArticle(NewsSection.NewArticle article) {
        String sql = """
                INSERT INTO news_articles (title, link, original_link, published_at,
                    content_summary, title_embedding, summary_embedding)
                VALUES (?, ?, ?, ?, ?, ?::vector, ?::vector)
                ON CONFLICT (link) WHERE link IS NOT NULL DO NOTHING
                RETURNING news_article_id
                """;

        OffsetDateTime publishedAt = OffsetDateTime.parse(article.publishedAt(), DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        List<Long> ids = jdbcTemplate.query(sql,
                (rs, rowNum) -> rs.getLong(1),
                article.title(),
                article.link(),
                article.originalLink(),
                java.sql.Timestamp.from(publishedAt.toInstant()),
                article.contentSummary(),
                toVectorString(article.titleEmbedding()),
                toVectorString(article.summaryEmbedding())
        );

        return ids.isEmpty() ? null : ids.getFirst();
    }

    private Long insertDartDisclosure(DartSection.NewDisclosure disclosure) {
        String sql = """
                INSERT INTO dart_disclosures (corp_code, corp_name, stock_code, corp_cls,
                    report_nm, rcept_no, flr_nm, rcept_dt, rm, content,
                    content_summary, title_embedding, summary_embedding)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::vector, ?::vector)
                ON CONFLICT (rcept_no) DO NOTHING
                RETURNING dart_disclosure_id
                """;

        List<Long> ids = jdbcTemplate.query(sql,
                (rs, rowNum) -> rs.getLong(1),
                disclosure.corpCode(),
                disclosure.corpName(),
                disclosure.stockCode(),
                disclosure.corpCls(),
                disclosure.reportNm(),
                disclosure.rceptNo(),
                disclosure.flrNm(),
                disclosure.rceptDt(),
                disclosure.rm(),
                disclosure.content(),
                disclosure.contentSummary(),
                toVectorString(disclosure.titleEmbedding()),
                toVectorString(disclosure.summaryEmbedding())
        );

        return ids.isEmpty() ? null : ids.getFirst();
    }

    private void linkNewsToAccounts(Long articleId, List<Long> accountIds) {
        String sql = """
                INSERT INTO account_news_articles (account_id, news_article_id, created_at)
                VALUES (?, ?, NOW())
                ON CONFLICT (account_id, news_article_id) DO NOTHING
                """;

        for (Long accountId : accountIds) {
            jdbcTemplate.update(sql, accountId, articleId);
        }
    }

    private void linkDartToAccounts(Long disclosureId, List<Long> accountIds) {
        String sql = """
                INSERT INTO account_dart_disclosures (account_id, dart_disclosure_id, created_at)
                VALUES (?, ?, NOW())
                ON CONFLICT (account_id, dart_disclosure_id) DO NOTHING
                """;

        for (Long accountId : accountIds) {
            jdbcTemplate.update(sql, accountId, disclosureId);
        }
    }

    private Long findNewsArticleIdByLink(String link) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT news_article_id FROM news_articles WHERE link = ?",
                (rs, rowNum) -> rs.getLong(1),
                link
        );
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private Long findDartDisclosureIdByRceptNo(String rceptNo) {
        List<Long> ids = jdbcTemplate.query(
                "SELECT dart_disclosure_id FROM dart_disclosures WHERE rcept_no = ?",
                (rs, rowNum) -> rs.getLong(1),
                rceptNo
        );
        return ids.isEmpty() ? null : ids.getFirst();
    }

    private String toVectorString(List<Double> embedding) {
        if (embedding == null || embedding.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(embedding.get(i));
        }
        sb.append(']');
        return sb.toString();
    }

    public record SignalCollectResult(
            int totalAccounts,
            int newsInserted,
            int dartInserted,
            List<String> errors,
            Map<Long, List<Long>> newNewsPerAccount,
            Map<Long, List<Long>> mappedNewsPerAccount,
            Map<Long, List<Long>> newDartPerAccount,
            Map<Long, List<Long>> mappedDartPerAccount
    ) {}
}

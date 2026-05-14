package com.ssafy.fint.domain.signal.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public record SignalCollectResponse(
        int totalAccounts,
        NewsSection news,
        DartSection dart,
        List<String> errors
) {

    public record NewsSection(
            List<NewArticle> newArticles,
            List<ExistingLink> existingLinks
    ) {
        public record NewArticle(
                String title,
                String link,
                String originalLink,
                String publishedAt,
                String contentSummary,
                List<Double> titleEmbedding,
                List<Double> summaryEmbedding,
                List<Long> accountIds
        ) {}

        public record ExistingLink(
                String link,
                List<Long> accountIds
        ) {}
    }

    public record DartSection(
            List<NewDisclosure> newDisclosures,
            List<ExistingRceptNo> existingRceptNos
    ) {
        public record NewDisclosure(
                String corpCode,
                String corpName,
                String stockCode,
                String corpCls,
                String reportNm,
                String rceptNo,
                String flrNm,
                String rceptDt,
                String rm,
                String content,
                String contentSummary,
                List<Double> titleEmbedding,
                List<Double> summaryEmbedding,
                List<Long> accountIds
        ) {}

        public record ExistingRceptNo(
                String rceptNo,
                List<Long> accountIds
        ) {}
    }

    @SuppressWarnings("unchecked")
    public static SignalCollectResponse from(Map<String, Object> raw) {
        Map<String, Object> data = (Map<String, Object>) raw.getOrDefault("data", raw);

        int totalAccounts = ((Number) data.getOrDefault("total_accounts", 0)).intValue();
        List<String> errors = (List<String>) data.getOrDefault("errors", Collections.emptyList());

        NewsSection newsSection = parseNews((Map<String, Object>) data.get("news"));
        DartSection dartSection = parseDart((Map<String, Object>) data.get("dart"));

        return new SignalCollectResponse(totalAccounts, newsSection, dartSection, errors);
    }

    @SuppressWarnings("unchecked")
    private static NewsSection parseNews(Map<String, Object> newsMap) {
        if (newsMap == null) {
            return new NewsSection(Collections.emptyList(), Collections.emptyList());
        }

        List<Map<String, Object>> rawArticles =
                (List<Map<String, Object>>) newsMap.getOrDefault("new_articles", Collections.emptyList());

        List<NewsSection.NewArticle> newArticles = rawArticles.stream()
                .map(m -> new NewsSection.NewArticle(
                        (String) m.get("title"),
                        (String) m.get("link"),
                        (String) m.get("original_link"),
                        (String) m.get("published_at"),
                        (String) m.get("content_summary"),
                        toDoubleList(m.get("title_embedding")),
                        toDoubleList(m.get("summary_embedding")),
                        toLongList(m.get("account_ids"))
                ))
                .toList();

        List<Map<String, Object>> rawExisting =
                (List<Map<String, Object>>) newsMap.getOrDefault("existing_links", Collections.emptyList());

        List<NewsSection.ExistingLink> existingLinks = rawExisting.stream()
                .map(m -> new NewsSection.ExistingLink(
                        (String) m.get("link"),
                        toLongList(m.get("account_ids"))
                ))
                .toList();

        return new NewsSection(newArticles, existingLinks);
    }

    @SuppressWarnings("unchecked")
    private static DartSection parseDart(Map<String, Object> dartMap) {
        if (dartMap == null) {
            return new DartSection(Collections.emptyList(), Collections.emptyList());
        }

        List<Map<String, Object>> rawDisclosures =
                (List<Map<String, Object>>) dartMap.getOrDefault("new_disclosures", Collections.emptyList());

        List<DartSection.NewDisclosure> newDisclosures = rawDisclosures.stream()
                .map(m -> new DartSection.NewDisclosure(
                        (String) m.get("corp_code"),
                        (String) m.get("corp_name"),
                        (String) m.get("stock_code"),
                        (String) m.get("corp_cls"),
                        (String) m.get("report_nm"),
                        (String) m.get("rcept_no"),
                        (String) m.get("flr_nm"),
                        (String) m.get("rcept_dt"),
                        (String) m.get("rm"),
                        (String) m.get("content"),
                        (String) m.get("content_summary"),
                        toDoubleList(m.get("title_embedding")),
                        toDoubleList(m.get("summary_embedding")),
                        toLongList(m.get("account_ids"))
                ))
                .toList();

        List<Map<String, Object>> rawExisting =
                (List<Map<String, Object>>) dartMap.getOrDefault("existing_rcept_nos", Collections.emptyList());

        List<DartSection.ExistingRceptNo> existingRceptNos = rawExisting.stream()
                .map(m -> new DartSection.ExistingRceptNo(
                        (String) m.get("rcept_no"),
                        toLongList(m.get("account_ids"))
                ))
                .toList();

        return new DartSection(newDisclosures, existingRceptNos);
    }

    @SuppressWarnings("unchecked")
    private static List<Double> toDoubleList(Object obj) {
        if (obj == null) return Collections.emptyList();
        return ((List<Number>) obj).stream().map(Number::doubleValue).toList();
    }

    @SuppressWarnings("unchecked")
    private static List<Long> toLongList(Object obj) {
        if (obj == null) return Collections.emptyList();
        return ((List<Number>) obj).stream().map(Number::longValue).toList();
    }
}

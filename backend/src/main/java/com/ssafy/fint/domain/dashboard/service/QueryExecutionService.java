package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.DashboardErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryExecutionService {

    private static final Pattern SELECT_ONLY = Pattern.compile(
            "^\\s*SELECT\\b", Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FORBIDDEN_KEYWORDS = Pattern.compile(
            "\\b(INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|TRUNCATE|GRANT|REVOKE)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final int MAX_ROWS = 500;
    private static final Pattern PARAM_PLACEHOLDER = Pattern.compile(":p(\\d+)");
    private static final Pattern LIMIT_PARAM = Pattern.compile(
            "LIMIT\\s+:p\\d+", Pattern.CASE_INSENSITIVE
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> execute(String sourceQuery, Long tenantId) {
        validate(sourceQuery);

        String resolved = resolveAiParams(sourceQuery);
        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId);

        String limited = applyRowLimit(resolved);

        try {
            return jdbcTemplate.queryForList(limited, params);
        } catch (Exception e) {
            log.warn("source_query 실행 실패: tenantId={}, error={}", tenantId, e.getMessage());
            throw new BusinessException(DashboardErrorCode.QUERY_EXECUTION_FAILED);
        }
    }

    /**
     * AI가 생성한 파라미터 플레이스홀더(:p1, :p2, ...)를 실행 가능한 형태로 변환.
     * :p1은 항상 tenant_id → :tenantId로 치환.
     * LIMIT :pN → 제거 (applyRowLimit이 대체).
     * 나머지 :pN → 제거하여 실행 오류 방지.
     */
    String resolveAiParams(String query) {
        String result = LIMIT_PARAM.matcher(query).replaceAll("");
        result = result.replace(":p1", ":tenantId");
        result = PARAM_PLACEHOLDER.matcher(result).replaceAll("NULL");
        return result;
    }

    private void validate(String sourceQuery) {
        if (sourceQuery == null || sourceQuery.isBlank()) {
            throw new BusinessException(DashboardErrorCode.QUERY_EXECUTION_FAILED);
        }
        if (!SELECT_ONLY.matcher(sourceQuery).find()) {
            throw new BusinessException(DashboardErrorCode.QUERY_NOT_SELECT);
        }
        if (FORBIDDEN_KEYWORDS.matcher(sourceQuery).find()) {
            throw new BusinessException(DashboardErrorCode.QUERY_NOT_SELECT);
        }
    }

    String applyRowLimit(String query) {
        String upper = query.toUpperCase().trim();
        if (upper.contains("LIMIT")) {
            return query;
        }
        return query.stripTrailing().replaceAll(";$", "") + " LIMIT " + MAX_ROWS;
    }
}

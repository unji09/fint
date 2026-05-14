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

    private final NamedParameterJdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> execute(String sourceQuery, Long tenantId) {
        validate(sourceQuery);

        MapSqlParameterSource params = new MapSqlParameterSource("tenantId", tenantId);

        String limited = applyRowLimit(sourceQuery);

        try {
            return jdbcTemplate.queryForList(limited, params);
        } catch (Exception e) {
            log.warn("source_query 실행 실패: tenantId={}, error={}", tenantId, e.getMessage());
            throw new BusinessException(DashboardErrorCode.QUERY_EXECUTION_FAILED);
        }
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

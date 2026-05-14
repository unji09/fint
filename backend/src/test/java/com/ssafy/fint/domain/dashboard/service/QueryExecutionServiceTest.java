package com.ssafy.fint.domain.dashboard.service;

import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.DashboardErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryExecutionServiceTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private QueryExecutionService queryExecutionService;

    @Test
    @DisplayName("SELECT 쿼리가 정상 실행되고 결과를 반환한다.")
    void executeSelectQuery() {
        String sql = "SELECT name, count FROM accounts WHERE tenant_id = :tenantId";
        List<Map<String, Object>> expected = List.of(
                Map.of("name", "A사", "count", 5),
                Map.of("name", "B사", "count", 3)
        );
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class)))
                .thenReturn(expected);

        List<Map<String, Object>> result = queryExecutionService.execute(sql, 1L);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("name")).isEqualTo("A사");

        ArgumentCaptor<SqlParameterSource> captor = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbcTemplate).queryForList(anyString(), captor.capture());
        assertThat(captor.getValue().getValue("tenantId")).isEqualTo(1L);
    }

    @Test
    @DisplayName("null 쿼리는 QUERY_EXECUTION_FAILED 로 차단된다.")
    void rejectNullQuery() {
        assertThatThrownBy(() -> queryExecutionService.execute(null, 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.QUERY_EXECUTION_FAILED);
    }

    @Test
    @DisplayName("빈 문자열 쿼리는 QUERY_EXECUTION_FAILED 로 차단된다.")
    void rejectBlankQuery() {
        assertThatThrownBy(() -> queryExecutionService.execute("  ", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.QUERY_EXECUTION_FAILED);
    }

    @Test
    @DisplayName("INSERT 쿼리는 QUERY_NOT_SELECT 로 차단된다.")
    void rejectInsertQuery() {
        assertThatThrownBy(() -> queryExecutionService.execute(
                "INSERT INTO accounts (name) VALUES ('hack')", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.QUERY_NOT_SELECT);
    }

    @Test
    @DisplayName("DELETE 쿼리는 QUERY_NOT_SELECT 로 차단된다.")
    void rejectDeleteQuery() {
        assertThatThrownBy(() -> queryExecutionService.execute(
                "DELETE FROM accounts", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.QUERY_NOT_SELECT);
    }

    @Test
    @DisplayName("DROP 키워드가 포함된 쿼리는 QUERY_NOT_SELECT 로 차단된다.")
    void rejectDropInSelect() {
        assertThatThrownBy(() -> queryExecutionService.execute(
                "SELECT 1; DROP TABLE accounts", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.QUERY_NOT_SELECT);
    }

    @Test
    @DisplayName("LIMIT 이 없는 쿼리에 자동으로 LIMIT 500 이 추가된다.")
    void appendLimitWhenMissing() {
        String sql = "SELECT name FROM accounts";
        String result = queryExecutionService.applyRowLimit(sql);
        assertThat(result).endsWith("LIMIT 500");
    }

    @Test
    @DisplayName("이미 LIMIT 이 있는 쿼리는 변경하지 않는다.")
    void preserveExistingLimit() {
        String sql = "SELECT name FROM accounts LIMIT 10";
        String result = queryExecutionService.applyRowLimit(sql);
        assertThat(result).isEqualTo(sql);
    }

    @Test
    @DisplayName("세미콜론이 있는 쿼리는 세미콜론 제거 후 LIMIT 을 추가한다.")
    void removeSemicolonBeforeLimit() {
        String sql = "SELECT name FROM accounts;";
        String result = queryExecutionService.applyRowLimit(sql);
        assertThat(result).endsWith("LIMIT 500");
        assertThat(result).doesNotContain(";");
    }

    @Test
    @DisplayName("JDBC 실행 중 예외가 발생하면 QUERY_EXECUTION_FAILED 로 래핑된다.")
    void wrapJdbcException() {
        when(jdbcTemplate.queryForList(anyString(), any(SqlParameterSource.class)))
                .thenThrow(new RuntimeException("bad sql"));

        assertThatThrownBy(() -> queryExecutionService.execute(
                "SELECT 1 FROM dual", 1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DashboardErrorCode.QUERY_EXECUTION_FAILED);
    }
}

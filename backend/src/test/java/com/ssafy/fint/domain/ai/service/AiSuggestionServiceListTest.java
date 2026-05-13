package com.ssafy.fint.domain.ai.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.ai.dto.NextActionListResponse;
import com.ssafy.fint.domain.ai.entity.AiSuggestion;
import com.ssafy.fint.domain.ai.entity.AiSuggestionRelatedType;
import com.ssafy.fint.domain.ai.client.NextActionClient;
import com.ssafy.fint.domain.ai.repository.AiSuggestionRepository;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.deal.repository.PipelineStageRepository;
import com.ssafy.fint.domain.notification.service.NotificationService;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import com.ssafy.fint.global.exception.AccountErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 고객사 Next Action 목록 조회(GET /accounts/{accountId}/ai/next-actions) 단위 테스트.
 * tenant 격리는 Repository 레이어 책임이라 mock 으로 빈 결과를 흘리는 것으로 검증을 대체한다.
 */
@ExtendWith(MockitoExtension.class)
class AiSuggestionServiceListTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 99L;
    private static final Long ACCOUNT_ID = 7L;

    @Mock private AccountRepository accountRepository;
    @Mock private AiSuggestionRepository aiSuggestionRepository;
    @Mock private PipelineStageRepository pipelineStageRepository;
    @Mock private NextActionClient nextActionClient;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private AiSuggestionService aiSuggestionService;

    private final CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");

    @Test
    @DisplayName("정상 조회 시 reason jsonb 의 category/successProbability 가 매핑되어 반환된다.")
    void listReturnsMappedSuggestions() {
        Account account = newAccount(ACCOUNT_ID);
        AiSuggestion s1 = newSuggestion(account, 1L, "시장 점유율 확대를 위한 채널 다각화",
                Map.of("category", "시장 확장 전략", "successProbability", 76));
        AiSuggestion s2 = newSuggestion(account, 2L, "ROI 기반 아키텍처 재설계안 리뷰",
                Map.of("category", "ROI 기반 전략", "successProbability", 89));

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(aiSuggestionRepository.findAllByAccountIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(List.of(s1, s2));

        List<NextActionListResponse> res = aiSuggestionService.findNextActions(me, ACCOUNT_ID);

        assertThat(res).hasSize(2);
        assertThat(res.get(0).suggestionId()).isEqualTo(1L);
        assertThat(res.get(0).title()).isEqualTo("시장 점유율 확대를 위한 채널 다각화");
        assertThat(res.get(0).category()).isEqualTo("시장 확장 전략");
        assertThat(res.get(0).successProbability()).isEqualTo(76);
        assertThat(res.get(1).suggestionId()).isEqualTo(2L);
        assertThat(res.get(1).category()).isEqualTo("ROI 기반 전략");
        assertThat(res.get(1).successProbability()).isEqualTo(89);
    }

    @Test
    @DisplayName("AI 제안이 하나도 없으면 빈 리스트가 반환된다.")
    void listReturnsEmptyWhenNoSuggestions() {
        Account account = newAccount(ACCOUNT_ID);

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(aiSuggestionRepository.findAllByAccountIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(List.of());

        List<NextActionListResponse> res = aiSuggestionService.findNextActions(me, ACCOUNT_ID);

        assertThat(res).isEmpty();
    }

    @Test
    @DisplayName("reason.successProbability 가 Long/Double 로 들어와도 Integer 로 정규화된다.")
    void successProbabilityNormalizedFromAnyNumberType() {
        // PostgreSQL JSONB → Map 역직렬화 시 정수가 Long 으로, 소수가 Double 로 올 수 있다.
        Account account = newAccount(ACCOUNT_ID);
        AiSuggestion longCase = newSuggestion(account, 11L, "Long 케이스",
                Map.of("category", "전략", "successProbability", 76L));
        AiSuggestion doubleCase = newSuggestion(account, 12L, "Double 케이스",
                Map.of("category", "전략", "successProbability", 88.7));

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(aiSuggestionRepository.findAllByAccountIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(List.of(longCase, doubleCase));

        List<NextActionListResponse> res = aiSuggestionService.findNextActions(me, ACCOUNT_ID);

        assertThat(res.get(0).successProbability()).isEqualTo(76);
        assertThat(res.get(1).successProbability()).isEqualTo(88);
    }

    @Test
    @DisplayName("reason.successProbability 가 숫자가 아닌 타입(예: String)으로 들어오면 null 로 정규화된다.")
    void successProbabilityNullWhenNonNumberType() {
        Account account = newAccount(ACCOUNT_ID);
        AiSuggestion suggestion = newSuggestion(account, 14L, "잘못된 타입",
                Map.of("category", "전략", "successProbability", "76"));

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(aiSuggestionRepository.findAllByAccountIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(List.of(suggestion));

        List<NextActionListResponse> res = aiSuggestionService.findNextActions(me, ACCOUNT_ID);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).successProbability()).isNull();
    }

    @Test
    @DisplayName("reason 에 category/successProbability 가 누락되어도 NPE 없이 null 로 매핑된다.")
    void allowsMissingReasonKeys() {
        Account account = newAccount(ACCOUNT_ID);
        Map<String, Object> reason = new HashMap<>(); // 빈 reason
        AiSuggestion suggestion = newSuggestion(account, 13L, "키 누락", reason);

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(aiSuggestionRepository.findAllByAccountIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(List.of(suggestion));

        List<NextActionListResponse> res = aiSuggestionService.findNextActions(me, ACCOUNT_ID);

        assertThat(res).hasSize(1);
        assertThat(res.get(0).suggestionId()).isEqualTo(13L);
        assertThat(res.get(0).title()).isEqualTo("키 누락");
        assertThat(res.get(0).category()).isNull();
        assertThat(res.get(0).successProbability()).isNull();
    }

    @Test
    @DisplayName("Account 가 없거나 다른 테넌트면 ACCOUNT_NOT_FOUND 로 차단된다.")
    void notFoundWhenAccountMissingOrOtherTenant() {
        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiSuggestionService.findNextActions(me, ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }

    private Account newAccount(long accountId) {
        User owner = User.builder()
                .tenant(newTenant())
                .role(UserRole.MEMBER)
                .name("owner")
                .passwordHash("x")
                .build();
        ReflectionTestUtils.setField(owner, "userId", USER_ID);
        Account account = Account.builder()
                .name("ACME")
                .industry("IT")
                .build();
        ReflectionTestUtils.setField(account, "accountId", accountId);
        return account;
    }

    private Tenant newTenant() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + TENANT_ID).build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);
        return tenant;
    }

    private PipelineStage newStage() {
        PipelineStage stage = PipelineStage.builder()
                .tenant(newTenant())
                .name("제안")
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(stage, "pipelineStageId", 10L);
        return stage;
    }

    private AiSuggestion newSuggestion(Account account, long suggestionId, String title,
                                       Map<String, Object> reason) {
        AiSuggestion suggestion = AiSuggestion.builder()
                .account(account)
                .pipelineStage(newStage())
                .title(title)
                .content("내용")
                .relatedType(AiSuggestionRelatedType.ACCOUNT)
                .reason(reason)
                .build();
        ReflectionTestUtils.setField(suggestion, "aiSuggestionId", suggestionId);
        return suggestion;
    }
}

package com.ssafy.fint.domain.ai.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.ai.dto.NextActionDetailResponse;
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
import com.ssafy.fint.global.exception.AiErrorCode;
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
 * 고객사 Next Action 상세 조회(GET /accounts/{accountId}/ai/next-actions/{suggestionId}) 단위 테스트.
 */
@ExtendWith(MockitoExtension.class)
class AiSuggestionServiceDetailTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 99L;
    private static final Long ACCOUNT_ID = 7L;
    private static final Long SUGGESTION_ID = 2L;

    @Mock private AccountRepository accountRepository;
    @Mock private AiSuggestionRepository aiSuggestionRepository;
    @Mock private PipelineStageRepository pipelineStageRepository;
    @Mock private NextActionClient nextActionClient;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private AiSuggestionService aiSuggestionService;

    private final CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");

    @Test
    @DisplayName("정상 조회 시 reason jsonb 의 모든 키(sources/recommendedScript/caution 포함)가 매핑되어 반환된다.")
    void detailReturnsMappedSuggestion() {
        Account account = newAccount(ACCOUNT_ID);
        Map<String, Object> sources = Map.of(
                "news", List.of(Map.of("title", "클라우드 전환 비용 최적화 트렌드 (2024.Q1)")),
                "dart", List.of(Map.of("title", "분기 실적 보고서 내 IT 인프라 유지비용 분석")),
                "crm", List.of(Map.of("summary", "고객 데이터 유실 위험 방지 및 가용성 개선 요청"))
        );
        Map<String, Object> reason = Map.of(
                "category", "ROI 기반 전략",
                "successProbability", 89,
                "sources", sources,
                "recommendedScript", "지난 미팅에서 말씀하신 ROI 최적화 관점을 반영해 아키텍처를 재설계했습니다.",
                "caution", "상대방의 침묵이 5초 이상 지속될 시, 기술 지원 기간 보장 카드를 제시하세요."
        );
        AiSuggestion suggestion = newSuggestion(account, SUGGESTION_ID, "ROI 기반 아키텍처 재설계안 리뷰", reason);

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(aiSuggestionRepository.findByIdAndAccountIdAndTenantId(SUGGESTION_ID, ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(suggestion));

        NextActionDetailResponse res = aiSuggestionService.findNextActionDetail(me, ACCOUNT_ID, SUGGESTION_ID);

        assertThat(res.suggestionId()).isEqualTo(SUGGESTION_ID);
        assertThat(res.title()).isEqualTo("ROI 기반 아키텍처 재설계안 리뷰");
        assertThat(res.category()).isEqualTo("ROI 기반 전략");
        assertThat(res.successProbability()).isEqualTo(89);
        assertThat(res.sources()).isEqualTo(sources);
        assertThat(res.recommendedScript()).startsWith("지난 미팅에서 말씀하신 ROI 최적화");
        assertThat(res.caution()).contains("기술 지원 기간 보장 카드");
    }

    @Test
    @DisplayName("reason 의 nullable 키(sources/recommendedScript/caution) 누락 시 null 로 매핑된다.")
    void allowsMissingOptionalReasonKeys() {
        Account account = newAccount(ACCOUNT_ID);
        Map<String, Object> reason = new HashMap<>();
        reason.put("category", "ROI 기반 전략");
        reason.put("successProbability", 70);
        // sources / recommendedScript / caution 누락
        AiSuggestion suggestion = newSuggestion(account, SUGGESTION_ID, "필수 키만 존재", reason);

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(aiSuggestionRepository.findByIdAndAccountIdAndTenantId(SUGGESTION_ID, ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(suggestion));

        NextActionDetailResponse res = aiSuggestionService.findNextActionDetail(me, ACCOUNT_ID, SUGGESTION_ID);

        assertThat(res.suggestionId()).isEqualTo(SUGGESTION_ID);
        assertThat(res.category()).isEqualTo("ROI 기반 전략");
        assertThat(res.successProbability()).isEqualTo(70);
        assertThat(res.sources()).isNull();
        assertThat(res.recommendedScript()).isNull();
        assertThat(res.caution()).isNull();
    }

    @Test
    @DisplayName("Account 가 없거나 다른 테넌트면 ACCOUNT_NOT_FOUND 로 차단된다.")
    void notFoundWhenAccountMissingOrOtherTenant() {
        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiSuggestionService.findNextActionDetail(me, ACCOUNT_ID, SUGGESTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("Account 는 있지만 suggestion 이 미존재/타 account/타 tenant 면 AI_SUGGESTION_NOT_FOUND 로 차단된다.")
    void notFoundWhenSuggestionMissing() {
        Account account = newAccount(ACCOUNT_ID);

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(aiSuggestionRepository.findByIdAndAccountIdAndTenantId(SUGGESTION_ID, ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiSuggestionService.findNextActionDetail(me, ACCOUNT_ID, SUGGESTION_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AiErrorCode.AI_SUGGESTION_NOT_FOUND);
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

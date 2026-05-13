package com.ssafy.fint.domain.ai.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.ai.client.NextActionAiResponse;
import com.ssafy.fint.domain.ai.client.NextActionClient;
import com.ssafy.fint.domain.ai.dto.NextActionCreateRequest;
import com.ssafy.fint.domain.ai.dto.NextActionCreateResponse;
import com.ssafy.fint.domain.ai.entity.AiSuggestion;
import com.ssafy.fint.domain.ai.entity.AiSuggestionRelatedType;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NextActionCreateServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 99L;
    private static final Long ACCOUNT_ID = 7L;
    private static final Long STAGE_ID = 10L;

    @Mock private AccountRepository accountRepository;
    @Mock private AiSuggestionRepository aiSuggestionRepository;
    @Mock private PipelineStageRepository pipelineStageRepository;
    @Mock private NextActionClient nextActionClient;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private AiSuggestionService aiSuggestionService;

    private final CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");

    @Test
    @DisplayName("정상 — FastAPI 응답을 AiSuggestion 으로 저장하고 WebSocket push 후 응답을 반환한다.")
    void createSuccess() {
        Account account = newAccount(ACCOUNT_ID, "(주)삼성전자");
        PipelineStage stage = newStage(STAGE_ID, "제안");
        NextActionAiResponse aiResponse = new NextActionAiResponse(
                "클라우드 전환 비용 절감 제안",
                "최근 인프라 비용 증가 이슈 감지",
                "시장 확장 전략",
                89,
                Map.of("news", java.util.List.of(), "dart", java.util.List.of(), "crm", java.util.List.of()),
                "인프라 비용 절감 효과를 수치로 제시하세요",
                "기존 벤더 교체 리스크",
                STAGE_ID
        );

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(nextActionClient.generate(TENANT_ID, ACCOUNT_ID, null))
                .thenReturn(aiResponse);
        when(pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(STAGE_ID, TENANT_ID))
                .thenReturn(Optional.of(stage));
        when(aiSuggestionRepository.save(any(AiSuggestion.class)))
                .thenAnswer(invocation -> {
                    AiSuggestion s = invocation.getArgument(0);
                    ReflectionTestUtils.setField(s, "aiSuggestionId", 100L);
                    ReflectionTestUtils.setField(s, "createdAt", OffsetDateTime.now());
                    return s;
                });

        NextActionCreateRequest request = new NextActionCreateRequest(ACCOUNT_ID, null);
        NextActionCreateResponse response = aiSuggestionService.createNextAction(me, request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.title()).isEqualTo("클라우드 전환 비용 절감 제안");
        assertThat(response.description()).isEqualTo("최근 인프라 비용 증가 이슈 감지");
        assertThat(response.category()).isEqualTo("시장 확장 전략");
        assertThat(response.successProbability()).isEqualTo(89);
        assertThat(response.recommendedScript()).isEqualTo("인프라 비용 절감 효과를 수치로 제시하세요");
        assertThat(response.risk()).isEqualTo("기존 벤더 교체 리스크");
        assertThat(response.createdAt()).isNotNull();

        verify(notificationService).pushNotification(any(AiSuggestion.class));
    }

    @Test
    @DisplayName("저장된 엔티티의 reason JSONB 에 category/successProbability/sources/recommendedScript/risk 가 포함된다.")
    void reasonJsonbContainsAllFields() {
        Account account = newAccount(ACCOUNT_ID, "(주)삼성전자");
        PipelineStage stage = newStage(STAGE_ID, "제안");
        Map<String, Object> sources = Map.of("news", java.util.List.of("뉴스1"), "dart", java.util.List.of(), "crm", java.util.List.of());
        NextActionAiResponse aiResponse = new NextActionAiResponse(
                "제목", "설명", "전략", 75, sources, "멘트", "리스크", STAGE_ID
        );

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(nextActionClient.generate(TENANT_ID, ACCOUNT_ID, "추가 맥락"))
                .thenReturn(aiResponse);
        when(pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(STAGE_ID, TENANT_ID))
                .thenReturn(Optional.of(stage));
        when(aiSuggestionRepository.save(any(AiSuggestion.class)))
                .thenAnswer(invocation -> {
                    AiSuggestion s = invocation.getArgument(0);
                    ReflectionTestUtils.setField(s, "aiSuggestionId", 1L);
                    ReflectionTestUtils.setField(s, "createdAt", OffsetDateTime.now());
                    return s;
                });

        aiSuggestionService.createNextAction(me, new NextActionCreateRequest(ACCOUNT_ID, "추가 맥락"));

        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionRepository).save(captor.capture());
        AiSuggestion saved = captor.getValue();

        assertThat(saved.getTitle()).isEqualTo("제목");
        assertThat(saved.getContent()).isEqualTo("설명");
        assertThat(saved.getRelatedType()).isEqualTo(AiSuggestionRelatedType.ACCOUNT);
        assertThat(saved.getReason()).containsEntry("category", "전략");
        assertThat(saved.getReason()).containsEntry("successProbability", 75);
        assertThat(saved.getReason()).containsEntry("recommendedScript", "멘트");
        assertThat(saved.getReason()).containsEntry("risk", "리스크");
        assertThat(saved.getReason()).containsKey("sources");
    }

    @Test
    @DisplayName("Account 가 존재하지 않으면 ACCOUNT_NOT_FOUND 예외가 발생한다.")
    void accountNotFound() {
        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiSuggestionService.createNextAction(me, new NextActionCreateRequest(ACCOUNT_ID, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("FastAPI 가 반환한 pipelineStageId 가 테넌트에 존재하지 않으면 INVALID_AI_RESPONSE 예외가 발생한다.")
    void invalidPipelineStage() {
        Account account = newAccount(ACCOUNT_ID, "(주)삼성전자");
        NextActionAiResponse aiResponse = new NextActionAiResponse(
                "제목", "설명", "전략", 50, Map.of(), "멘트", "리스크", 999L
        );

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(nextActionClient.generate(TENANT_ID, ACCOUNT_ID, null))
                .thenReturn(aiResponse);
        when(pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(999L, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiSuggestionService.createNextAction(me, new NextActionCreateRequest(ACCOUNT_ID, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AiErrorCode.INVALID_AI_RESPONSE);
    }

    private Account newAccount(long accountId, String name) {
        User owner = User.builder()
                .tenant(newTenant())
                .role(UserRole.MEMBER)
                .name("owner")
                .passwordHash("x")
                .build();
        ReflectionTestUtils.setField(owner, "userId", USER_ID);
        Account account = Account.builder()
                .name(name)
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

    private PipelineStage newStage(long stageId, String name) {
        PipelineStage stage = PipelineStage.builder()
                .tenant(newTenant())
                .name(name)
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(stage, "pipelineStageId", stageId);
        return stage;
    }
}

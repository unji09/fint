package com.ssafy.fint.domain.ai.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.ai.client.NextActionAiResponse;
import com.ssafy.fint.domain.ai.client.NextActionClient;
import com.ssafy.fint.domain.ai.dto.NextActionCreateRequest;
import com.ssafy.fint.domain.ai.dto.NextActionCreateResponse;
import com.ssafy.fint.domain.ai.entity.AiSuggestion;
import com.ssafy.fint.domain.ai.entity.AiSuggestionRelatedType;
import com.ssafy.fint.domain.ai.entity.TriggerType;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
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
    @DisplayName("정상 — FastAPI 배열 응답을 모두 AiSuggestion 으로 저장하고 응답을 반환한다.")
    void createSuccess() {
        Account account = newAccount(ACCOUNT_ID, "(주)삼성전자");
        PipelineStage stage = newStage(STAGE_ID, "제안");

        List<NextActionAiResponse> aiResponses = List.of(
                new NextActionAiResponse(
                        "클라우드 전환 비용 절감 제안",
                        "최근 인프라 비용 증가 이슈 감지",
                        "COST_REDUCTION", "ACCOUNT",
                        4.5, 89,
                        Map.of("news", List.of(), "dart", List.of(), "crm", List.of()),
                        "인프라 비용 절감 효과를 수치로 제시하세요",
                        STAGE_ID
                ),
                new NextActionAiResponse(
                        "EB 식별 및 컨택",
                        "EB 미참여 상태",
                        "Qualification", "ACCOUNT",
                        3.5, 72,
                        Map.of("news", List.of(), "dart", List.of(), "crm", List.of()),
                        "Champion 에게 EB 소개 요청",
                        STAGE_ID
                )
        );

        NextActionCreateRequest request = new NextActionCreateRequest(
                ACCOUNT_ID, TriggerType.EXTERNAL_SIGNAL_UPDATED,
                List.of(101L, 102L), List.of(55L), null, null);

        AtomicLong idSeq = new AtomicLong(100L);
        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(nextActionClient.generate(eq(TENANT_ID), any(NextActionCreateRequest.class)))
                .thenReturn(aiResponses);
        when(pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(STAGE_ID, TENANT_ID))
                .thenReturn(Optional.of(stage));
        when(aiSuggestionRepository.save(any(AiSuggestion.class)))
                .thenAnswer(invocation -> {
                    AiSuggestion s = invocation.getArgument(0);
                    ReflectionTestUtils.setField(s, "aiSuggestionId", idSeq.getAndIncrement());
                    ReflectionTestUtils.setField(s, "createdAt", OffsetDateTime.now());
                    return s;
                });

        List<NextActionCreateResponse> responses = aiSuggestionService.createNextAction(me, request);

        assertThat(responses).hasSize(2);

        NextActionCreateResponse first = responses.get(0);
        assertThat(first.nextActionId()).isEqualTo(100L);
        assertThat(first.accountId()).isEqualTo(ACCOUNT_ID);
        assertThat(first.accountName()).isEqualTo("(주)삼성전자");
        assertThat(first.action()).isEqualTo("클라우드 전환 비용 절감 제안");
        assertThat(first.importanceScore()).isEqualTo(4.5);

        NextActionCreateResponse second = responses.get(1);
        assertThat(second.nextActionId()).isEqualTo(101L);
        assertThat(second.action()).isEqualTo("EB 식별 및 컨택");
        assertThat(second.importanceScore()).isEqualTo(3.5);

        verify(aiSuggestionRepository, times(2)).save(any(AiSuggestion.class));
        verify(notificationService, times(1)).pushNotification(any(AiSuggestion.class));
    }

    @Test
    @DisplayName("저장된 엔티티에 category/successProbability/importanceScore 가 직접 필드로 저장된다.")
    void entityFieldsStoredDirectly() {
        Account account = newAccount(ACCOUNT_ID, "(주)삼성전자");
        PipelineStage stage = newStage(STAGE_ID, "제안");
        Map<String, Object> sources = Map.of("news", List.of("뉴스1"), "dart", List.of(), "crm", List.of());

        List<NextActionAiResponse> aiResponses = List.of(new NextActionAiResponse(
                "제목", "설명", "MARKET_EXPANSION", "ACCOUNT",
                3.8, 75, sources, "멘트", STAGE_ID
        ));

        NextActionCreateRequest request = new NextActionCreateRequest(
                ACCOUNT_ID, TriggerType.NEWS_UPDATED,
                List.of(201L), null, null, "추가 맥락");

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(nextActionClient.generate(eq(TENANT_ID), any(NextActionCreateRequest.class)))
                .thenReturn(aiResponses);
        when(pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(STAGE_ID, TENANT_ID))
                .thenReturn(Optional.of(stage));
        when(aiSuggestionRepository.save(any(AiSuggestion.class)))
                .thenAnswer(invocation -> {
                    AiSuggestion s = invocation.getArgument(0);
                    ReflectionTestUtils.setField(s, "aiSuggestionId", 1L);
                    ReflectionTestUtils.setField(s, "createdAt", OffsetDateTime.now());
                    return s;
                });

        aiSuggestionService.createNextAction(me, request);

        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionRepository).save(captor.capture());
        AiSuggestion saved = captor.getValue();

        assertThat(saved.getTitle()).isEqualTo("제목");
        assertThat(saved.getContent()).isEqualTo("설명");
        assertThat(saved.getCategory()).isEqualTo("MARKET_EXPANSION");
        assertThat(saved.getSuccessProbability()).isEqualTo(75);
        assertThat(saved.getImportanceScore()).isEqualTo(3.8);
        assertThat(saved.getRelatedType()).isEqualTo(AiSuggestionRelatedType.ACCOUNT);
        assertThat(saved.getReason()).containsKey("sources");
        assertThat(saved.getReason()).containsEntry("recommendedScript", "멘트");
        verify(notificationService, times(0)).pushNotification(any(AiSuggestion.class));
    }

    @Test
    @DisplayName("triggerType 이 MEETING_CREATED 이면 relatedType 이 MEETING 으로 설정된다.")
    void meetingTriggerSetsRelatedTypeMeeting() {
        Account account = newAccount(ACCOUNT_ID, "(주)삼성전자");
        PipelineStage stage = newStage(STAGE_ID, "제안");

        List<NextActionAiResponse> aiResponses = List.of(new NextActionAiResponse(
                "미팅 후속 액션", "미팅 기반 추천", "FOLLOW_UP", null,
                3.0, 70, Map.of(), "멘트", STAGE_ID
        ));

        NextActionCreateRequest request = new NextActionCreateRequest(
                ACCOUNT_ID, TriggerType.MEETING_CREATED,
                null, null, List.of(77L), "미팅 기반 추천 요청");

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(nextActionClient.generate(eq(TENANT_ID), any(NextActionCreateRequest.class)))
                .thenReturn(aiResponses);
        when(pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(STAGE_ID, TENANT_ID))
                .thenReturn(Optional.of(stage));
        when(aiSuggestionRepository.save(any(AiSuggestion.class)))
                .thenAnswer(invocation -> {
                    AiSuggestion s = invocation.getArgument(0);
                    ReflectionTestUtils.setField(s, "aiSuggestionId", 1L);
                    ReflectionTestUtils.setField(s, "createdAt", OffsetDateTime.now());
                    return s;
                });

        aiSuggestionService.createNextAction(me, request);

        ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
        verify(aiSuggestionRepository).save(captor.capture());
        assertThat(captor.getValue().getRelatedType()).isEqualTo(AiSuggestionRelatedType.MEETING);
    }

    @Test
    @DisplayName("Account 가 존재하지 않으면 ACCOUNT_NOT_FOUND 예외가 발생한다.")
    void accountNotFound() {
        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        NextActionCreateRequest request = new NextActionCreateRequest(
                ACCOUNT_ID, TriggerType.NEWS_UPDATED, null, null, null, null);

        assertThatThrownBy(() -> aiSuggestionService.createNextAction(me, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
    }

    @Test
    @DisplayName("FastAPI 가 반환한 pipelineStageId 가 테넌트에 존재하지 않으면 INVALID_AI_RESPONSE 예외가 발생한다.")
    void invalidPipelineStage() {
        Account account = newAccount(ACCOUNT_ID, "(주)삼성전자");

        List<NextActionAiResponse> aiResponses = List.of(new NextActionAiResponse(
                "제목", "설명", "전략", "ACCOUNT",
                2.5, 50, Map.of(), "멘트", 999L
        ));

        NextActionCreateRequest request = new NextActionCreateRequest(
                ACCOUNT_ID, TriggerType.NEWS_UPDATED, null, null, null, null);

        when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                .thenReturn(Optional.of(account));
        when(nextActionClient.generate(eq(TENANT_ID), any(NextActionCreateRequest.class)))
                .thenReturn(aiResponses);
        when(pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(999L, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> aiSuggestionService.createNextAction(me, request))
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

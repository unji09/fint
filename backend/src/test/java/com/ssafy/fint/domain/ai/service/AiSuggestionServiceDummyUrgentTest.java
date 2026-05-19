package com.ssafy.fint.domain.ai.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.ai.client.NextActionClient;
import com.ssafy.fint.domain.ai.entity.AiSuggestion;
import com.ssafy.fint.domain.ai.repository.AiSuggestionRepository;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.deal.repository.PipelineStageRepository;
import com.ssafy.fint.domain.notification.service.NotificationService;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import com.ssafy.fint.global.exception.AccountErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSuggestionServiceDummyUrgentTest {

    private static final Long TENANT_ID = 1L;
    private static final Long ACCOUNT_ID = 7L;

    @Mock private AccountRepository accountRepository;
    @Mock private AiSuggestionRepository aiSuggestionRepository;
    @Mock private PipelineStageRepository pipelineStageRepository;
    @Mock private NextActionClient nextActionClient;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private AiSuggestionService aiSuggestionService;

    @Nested
    @DisplayName("createDummyUrgentSignal")
    class CreateDummyUrgentSignal {

        @Test
        @DisplayName("importanceScore >= 4.0이면 AiSuggestion 저장 후 WebSocket 알림을 발송한다")
        void pushesNotificationWhenUrgent() {
            Account account = newAccount();
            PipelineStage stage = newStage();

            when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                    .thenReturn(Optional.of(account));
            when(pipelineStageRepository.findFirstByTenant_TenantIdOrderBySortOrderAsc(TENANT_ID))
                    .thenReturn(Optional.of(stage));
            when(aiSuggestionRepository.save(any(AiSuggestion.class)))
                    .thenAnswer(invocation -> {
                        AiSuggestion s = invocation.getArgument(0);
                        ReflectionTestUtils.setField(s, "aiSuggestionId", 100L);
                        return s;
                    });

            aiSuggestionService.createDummyUrgentSignal(TENANT_ID, ACCOUNT_ID, 5.0);

            ArgumentCaptor<AiSuggestion> captor = ArgumentCaptor.forClass(AiSuggestion.class);
            verify(aiSuggestionRepository).save(captor.capture());
            AiSuggestion saved = captor.getValue();

            assertThat(saved.getImportanceScore()).isEqualTo(5.0);
            assertThat(saved.getTitle()).contains("Champion 위기 대응");
            assertThat(saved.getCategory()).isEqualTo("Champion Building & Multi-thread");
            assertThat(saved.getSuccessProbability()).isEqualTo(68);

            verify(notificationService).pushNotification(saved);
        }

        @Test
        @DisplayName("importanceScore < 4.0이면 저장만 하고 알림을 발송하지 않는다")
        void doesNotPushWhenBelowThreshold() {
            Account account = newAccount();
            PipelineStage stage = newStage();

            when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                    .thenReturn(Optional.of(account));
            when(pipelineStageRepository.findFirstByTenant_TenantIdOrderBySortOrderAsc(TENANT_ID))
                    .thenReturn(Optional.of(stage));
            when(aiSuggestionRepository.save(any(AiSuggestion.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            aiSuggestionService.createDummyUrgentSignal(TENANT_ID, ACCOUNT_ID, 3.0);

            verify(aiSuggestionRepository).save(any(AiSuggestion.class));
            verify(notificationService, never()).pushNotification(any());
        }

        @Test
        @DisplayName("존재하지 않는 Account면 ACCOUNT_NOT_FOUND 예외가 발생한다")
        void throwsWhenAccountNotFound() {
            when(accountRepository.findByIdAndTenantId(ACCOUNT_ID, TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    aiSuggestionService.createDummyUrgentSignal(TENANT_ID, ACCOUNT_ID, 5.0))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AccountErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private Account newAccount() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C1").build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);

        User owner = User.builder()
                .tenant(tenant)
                .role(UserRole.MEMBER)
                .name("owner")
                .passwordHash("x")
                .build();
        ReflectionTestUtils.setField(owner, "userId", 99L);

        Account account = Account.builder()
                .name("삼성전자")
                .industry("반도체")
                .build();
        ReflectionTestUtils.setField(account, "accountId", ACCOUNT_ID);
        return account;
    }

    private PipelineStage newStage() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C1").build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);

        PipelineStage stage = PipelineStage.builder()
                .tenant(tenant)
                .name("평가")
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(stage, "pipelineStageId", 10L);
        return stage;
    }
}

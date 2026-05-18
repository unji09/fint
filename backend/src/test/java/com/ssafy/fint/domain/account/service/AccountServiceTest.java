package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.client.CompanyEnrichClient;
import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.AccountUserAssignment;
import com.ssafy.fint.domain.account.repository.AccountExternalInfoRepository;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.AccountUserAssignmentRepository;
import com.ssafy.fint.domain.account.repository.TemperatureHistoryRepository;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 등록 단위 테스트")
class AccountServiceTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long CURRENT_TENANT_ID = 1L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountUserAssignmentRepository accountUserAssignmentRepository;

    @Mock
    @SuppressWarnings("unused")
    private AccountExternalInfoRepository accountExternalInfoRepository;

    @Mock
    @SuppressWarnings("unused")
    private TemperatureHistoryRepository temperatureHistoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    @SuppressWarnings("unused")
    private ApplicationEventPublisher eventPublisher;

    @Mock
    @SuppressWarnings("unused")
    private CompanyEnrichClient companyEnrichClient;

    @InjectMocks
    private AccountService accountService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("register - case2 (신규 등록)")
    class RegisterCase2 {

        @Test
        @DisplayName("정상 요청 시 account + assignment 가 모두 저장되고 accountId 가 반환된다")
        void 정상_등록되면_account_assignment_저장_후_accountId_반환() {
            setAuthentication(CURRENT_USER_ID);

            User ownerMock = mock(User.class);
            given(userRepository.getReferenceById(CURRENT_USER_ID)).willReturn(ownerMock);

            Account savedMock = mock(Account.class);
            given(savedMock.getAccountId()).willReturn(10L);
            given(accountRepository.save(any(Account.class))).willReturn(savedMock);

            AccountRegisterRequest request = new AccountRegisterRequest(
                    null, "(주)삼성전자", "전자/반도체", "124-81-00998");

            AccountRegisterResponse response = accountService.register(request);

            assertThat(response.accountId()).isEqualTo(10L);
            verify(accountRepository).save(any(Account.class));
            verify(accountUserAssignmentRepository).save(any(AccountUserAssignment.class));
        }

        @Test
        @DisplayName("name 누락 시 INVALID_INPUT 예외가 발생한다")
        void name_누락하면_INVALID_INPUT_예외() {
            setAuthentication(CURRENT_USER_ID);
            given(userRepository.getReferenceById(CURRENT_USER_ID)).willReturn(mock(User.class));

            AccountRegisterRequest request = new AccountRegisterRequest(
                    null, null, "전자", null);

            assertThatThrownBy(() -> accountService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.INVALID_INPUT);

            verify(accountRepository, never()).save(any(Account.class));
        }

        @Test
        @DisplayName("industry 누락 시 INVALID_INPUT 예외가 발생한다")
        void industry_누락하면_INVALID_INPUT_예외() {
            setAuthentication(CURRENT_USER_ID);
            given(userRepository.getReferenceById(CURRENT_USER_ID)).willReturn(mock(User.class));

            AccountRegisterRequest request = new AccountRegisterRequest(
                    null, "(주)삼성전자", null, null);

            assertThatThrownBy(() -> accountService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.INVALID_INPUT);
        }
    }

    @Nested
    @DisplayName("register - case1 (기존 account 책임자 등록)")
    class RegisterCase1 {

        @Test
        @DisplayName("기존 account 가 같은 tenant 에 있으면 assignment 가 추가되고 accountId 가 반환된다")
        void 기존_account_정상_매핑되면_accountId_반환() {
            setAuthentication(CURRENT_USER_ID);

            User ownerMock = mock(User.class);
            given(userRepository.getReferenceById(CURRENT_USER_ID)).willReturn(ownerMock);

            Account existingMock = mock(Account.class);
            given(existingMock.getAccountId()).willReturn(50L);
            when(accountRepository.findByIdAndTenantId(50L, CURRENT_TENANT_ID))
                    .thenReturn(Optional.of(existingMock));
            when(accountUserAssignmentRepository
                    .existsByAccount_AccountIdAndUser_UserId(50L, CURRENT_USER_ID))
                    .thenReturn(false);

            AccountRegisterRequest request = new AccountRegisterRequest(50L, null, null, null);

            AccountRegisterResponse response = accountService.register(request);

            assertThat(response.accountId()).isEqualTo(50L);
            verify(accountRepository, never()).save(any(Account.class));
            verify(accountUserAssignmentRepository).save(any(AccountUserAssignment.class));
        }

        @Test
        @DisplayName("이미 책임자인 경우 idempotent 하게 동작하고 assignment 가 중복 저장되지 않는다")
        void 이미_책임자면_assignment_중복_저장_안됨() {
            setAuthentication(CURRENT_USER_ID);
            given(userRepository.getReferenceById(CURRENT_USER_ID)).willReturn(mock(User.class));

            Account existingMock = mock(Account.class);
            given(existingMock.getAccountId()).willReturn(50L);
            when(accountRepository.findByIdAndTenantId(50L, CURRENT_TENANT_ID))
                    .thenReturn(Optional.of(existingMock));
            when(accountUserAssignmentRepository
                    .existsByAccount_AccountIdAndUser_UserId(50L, CURRENT_USER_ID))
                    .thenReturn(true);

            AccountRegisterRequest request = new AccountRegisterRequest(50L, null, null, null);

            AccountRegisterResponse response = accountService.register(request);

            assertThat(response.accountId()).isEqualTo(50L);
            verify(accountUserAssignmentRepository, never()).save(any(AccountUserAssignment.class));
        }

        @Test
        @DisplayName("미존재 또는 타 테넌트 account 는 NOT_FOUND 로 차단된다")
        void 미존재_또는_타테넌트_account는_NOT_FOUND_예외() {
            setAuthentication(CURRENT_USER_ID);
            given(userRepository.getReferenceById(CURRENT_USER_ID)).willReturn(mock(User.class));

            when(accountRepository.findByIdAndTenantId(99L, CURRENT_TENANT_ID))
                    .thenReturn(Optional.empty());

            AccountRegisterRequest request = new AccountRegisterRequest(99L, null, null, null);

            assertThatThrownBy(() -> accountService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(CommonErrorCode.NOT_FOUND);

            verify(accountUserAssignmentRepository, never()).save(any(AccountUserAssignment.class));
        }
    }

    @Nested
    @DisplayName("register - 인증")
    class RegisterAuth {

        @Test
        @DisplayName("SecurityContext 가 비어있으면 INVALID_TOKEN 예외가 발생한다")
        void SecurityContext_없으면_INVALID_TOKEN_예외() {
            SecurityContextHolder.clearContext();
            AccountRegisterRequest request = new AccountRegisterRequest(
                    null, "(주)삼성전자", "전자/반도체", null);

            assertThatThrownBy(() -> accountService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("Principal 이 CustomUserDetails 가 아니면 INVALID_TOKEN 예외가 발생한다")
        void principal_타입_불일치하면_INVALID_TOKEN_예외() {
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    "anonymous-string-principal", null);
            SecurityContextHolder.getContext().setAuthentication(auth);

            AccountRegisterRequest request = new AccountRegisterRequest(
                    null, "(주)삼성전자", "전자/반도체", null);

            assertThatThrownBy(() -> accountService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }
    }

    private void setAuthentication(Long userId) {
        CustomUserDetails details = new CustomUserDetails(userId, CURRENT_TENANT_ID, "MEMBER");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}

package com.ssafy.fint.domain.account.service;

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
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 등록 단위 테스트")
class AccountServiceTest {

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

    @InjectMocks
    private AccountService accountService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("register - 고객사 등록 (case2: 신규)")
    class Register {

        @Test
        @DisplayName("정상 요청 시 account + assignment 가 모두 저장되고 accountId 가 반환된다")
        void 정상_등록되면_account_assignment_저장_후_accountId_반환() {
            Long userId = 1L;
            setAuthentication(userId);

            User ownerMock = mock(User.class);
            given(userRepository.getReferenceById(userId)).willReturn(ownerMock);

            Account savedMock = mock(Account.class);
            given(savedMock.getAccountId()).willReturn(10L);
            given(accountRepository.save(any(Account.class))).willReturn(savedMock);

            AccountRegisterRequest request = new AccountRegisterRequest(
                    "(주)삼성전자", "전자/반도체", "124-81-00998");

            AccountRegisterResponse response = accountService.register(request);

            assertThat(response.accountId()).isEqualTo(10L);
            verify(accountRepository).save(any(Account.class));
            verify(accountUserAssignmentRepository).save(any(AccountUserAssignment.class));
        }

        @Test
        @DisplayName("SecurityContext 가 비어있으면 INVALID_TOKEN 예외가 발생한다")
        void SecurityContext_없으면_INVALID_TOKEN_예외() {
            SecurityContextHolder.clearContext();
            AccountRegisterRequest request = new AccountRegisterRequest(
                    "(주)삼성전자", "전자/반도체", null);

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
                    "(주)삼성전자", "전자/반도체", null);

            assertThatThrownBy(() -> accountService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }
    }

    private void setAuthentication(Long userId) {
        CustomUserDetails details = new CustomUserDetails(userId, 1L, "MEMBER");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}

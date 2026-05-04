package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.dto.AccountRegisterRequest;
import com.ssafy.fint.domain.account.dto.AccountRegisterResponse;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
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
@DisplayName("AccountService 단위 테스트")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AccountService accountService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Nested
    @DisplayName("register - 고객사 등록")
    class Register {

        @Test
        @DisplayName("정상 요청 시 accountId 를 담은 응답을 반환한다")
        void 정상_등록되면_accountId_반환() {
            // given
            Long userId = 1L;
            setAuthentication(userId);

            User ownerMock = mock(User.class);
            given(ownerMock.getUserId()).willReturn(userId);
            given(userRepository.getReferenceById(userId)).willReturn(ownerMock);

            Account savedMock = mock(Account.class);
            given(savedMock.getAccountId()).willReturn(10L);
            given(accountRepository.save(any(Account.class))).willReturn(savedMock);

            AccountRegisterRequest request = new AccountRegisterRequest(
                    "(주)삼성전자", "전자/반도체", "124-81-00998");

            // when
            AccountRegisterResponse response = accountService.register(request);

            // then
            assertThat(response.accountId()).isEqualTo(10L);
            verify(accountRepository).save(any(Account.class));
        }

        @Test
        @DisplayName("SecurityContext 가 비어있으면 INVALID_TOKEN 예외가 발생한다")
        void SecurityContext_없으면_INVALID_TOKEN_예외() {
            // given
            SecurityContextHolder.clearContext();
            AccountRegisterRequest request = new AccountRegisterRequest(
                    "(주)삼성전자", "전자/반도체", null);

            // when, then
            assertThatThrownBy(() -> accountService.register(request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(AuthErrorCode.INVALID_TOKEN);
        }

        @Test
        @DisplayName("Principal 이 CustomUserDetails 가 아니면 INVALID_TOKEN 예외가 발생한다")
        void principal_타입_불일치하면_INVALID_TOKEN_예외() {
            // given
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    "anonymous-string-principal", null);
            SecurityContextHolder.getContext().setAuthentication(auth);

            AccountRegisterRequest request = new AccountRegisterRequest(
                    "(주)삼성전자", "전자/반도체", null);

            // when, then
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

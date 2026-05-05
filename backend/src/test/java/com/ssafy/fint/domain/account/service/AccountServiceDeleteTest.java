package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 고객사 단건 삭제(DELETE /accounts/{accountId}) 단위 테스트.
 * 본인 소유 + 같은 tenant 격리 규칙을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 삭제 단위 테스트")
class AccountServiceDeleteTest {

    private static final Long CURRENT_USER_ID = 10L;
    private static final Long CURRENT_TENANT_ID = 1L;
    private static final Long ACCOUNT_ID = 100L;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    @SuppressWarnings("unused")
    private UserRepository userRepository;

    @InjectMocks
    private AccountService accountService;

    @BeforeEach
    void setAuthentication() {
        CustomUserDetails principal = new CustomUserDetails(
                CURRENT_USER_ID, CURRENT_TENANT_ID, "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("본인 소유 account 는 정상 삭제되고 softDelete 가 호출된다.")
    void deleteOwnedAccount() {
        Account account = mock(Account.class);
        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(account));

        accountService.delete(ACCOUNT_ID);

        verify(account).softDelete();
    }

    @Test
    @DisplayName("미존재 또는 타 사용자·타 테넌트 소유 account 는 NOT_FOUND 로 차단된다.")
    void rejectMissingOrForeignAccount() {
        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.delete(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.NOT_FOUND);

        verify(accountRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("Repository 조회 시 현재 사용자·테넌트 ID 가 그대로 전달된다.")
    void passesCurrentUserAndTenantToRepository() {
        Account account = mock(Account.class);
        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(account));

        accountService.delete(ACCOUNT_ID);

        verify(accountRepository).findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID);
    }

    @Test
    @DisplayName("인증 컨텍스트가 없으면 INVALID_TOKEN 으로 차단된다.")
    void rejectWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> accountService.delete(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);

        verifyNoInteractions(accountRepository);
    }
}

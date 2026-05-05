package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.dto.AccountUpdateRequest;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 고객사 부분 수정(PATCH /accounts/{accountId}) 단위 테스트.
 * 본인 소유 + 같은 tenant 격리, partial update 동작을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 수정 단위 테스트")
class AccountServiceUpdateTest {

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
    @DisplayName("모든 필드 변경 시 의도 메서드가 모두 호출된다.")
    void updatesAllFields() {
        Account account = mock(Account.class);
        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(account));

        AccountUpdateRequest request = new AccountUpdateRequest(
                "(주)변경전자", "반도체", "999-99-99999");

        accountService.update(ACCOUNT_ID, request);

        verify(account).changeName("(주)변경전자");
        verify(account).changeIndustry("반도체");
        verify(account).changeBizNo("999-99-99999");
    }

    @Test
    @DisplayName("일부 필드만 전달하면 해당 필드의 의도 메서드만 호출된다.")
    void updatesOnlyProvidedFields() {
        Account account = mock(Account.class);
        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(account));

        AccountUpdateRequest request = new AccountUpdateRequest(null, "반도체", null);

        accountService.update(ACCOUNT_ID, request);

        verify(account, never()).changeName(any());
        verify(account).changeIndustry("반도체");
        verify(account, never()).changeBizNo(any());
    }

    @Test
    @DisplayName("모든 필드가 null 이면 어떤 의도 메서드도 호출되지 않는다 (no-op).")
    void noOpWhenAllFieldsNull() {
        Account account = mock(Account.class);
        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(account));

        AccountUpdateRequest request = new AccountUpdateRequest(null, null, null);

        accountService.update(ACCOUNT_ID, request);

        verify(account, never()).changeName(any());
        verify(account, never()).changeIndustry(any());
        verify(account, never()).changeBizNo(any());
    }

    @Test
    @DisplayName("미존재 또는 타 사용자·타 테넌트 소유 account 는 NOT_FOUND 로 차단된다.")
    void rejectMissingOrForeignAccount() {
        when(accountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACCOUNT_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.empty());

        AccountUpdateRequest request = new AccountUpdateRequest("이름", null, null);

        assertThatThrownBy(() -> accountService.update(ACCOUNT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(CommonErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("인증 컨텍스트가 없으면 INVALID_TOKEN 으로 차단된다.")
    void rejectWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        AccountUpdateRequest request = new AccountUpdateRequest("이름", null, null);

        assertThatThrownBy(() -> accountService.update(ACCOUNT_ID, request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);

        verifyNoInteractions(accountRepository);
    }
}

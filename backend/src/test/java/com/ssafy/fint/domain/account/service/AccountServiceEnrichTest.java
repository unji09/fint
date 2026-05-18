package com.ssafy.fint.domain.account.service;

import com.ssafy.fint.domain.account.client.CompanyEnrichApiResponse;
import com.ssafy.fint.domain.account.client.CompanyEnrichClient;
import com.ssafy.fint.domain.account.dto.AccountEnrichResponse;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountExternalInfoRepository;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.AccountUserAssignmentRepository;
import com.ssafy.fint.domain.account.repository.ContactRepository;
import com.ssafy.fint.domain.account.repository.TemperatureHistoryRepository;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.signal.repository.DartDisclosureRepository;
import com.ssafy.fint.domain.user.repository.UserRepository;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService.enrich 단위 테스트")
class AccountServiceEnrichTest {

    private static final Long USER_ID = 1L;
    private static final Long TENANT_ID = 1L;
    private static final Long ACCOUNT_ID = 10L;

    @Mock private AccountRepository accountRepository;
    @Mock private CompanyEnrichClient companyEnrichClient;

    @Mock @SuppressWarnings("unused") private AccountUserAssignmentRepository accountUserAssignmentRepository;
    @Mock @SuppressWarnings("unused") private AccountExternalInfoRepository accountExternalInfoRepository;
    @Mock @SuppressWarnings("unused") private DartDisclosureRepository dartDisclosureRepository;
    @Mock @SuppressWarnings("unused") private TemperatureHistoryRepository temperatureHistoryRepository;
    @Mock @SuppressWarnings("unused") private UserRepository userRepository;
    @Mock @SuppressWarnings("unused") private ActivityRepository activityRepository;
    @Mock @SuppressWarnings("unused") private ContactRepository contactRepository;
    @Mock @SuppressWarnings("unused") private DealRepository dealRepository;
    @Mock @SuppressWarnings("unused") private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private AccountService accountService;

    @BeforeEach
    void setAuthentication() {
        CustomUserDetails details = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");
        Authentication auth = new UsernamePasswordAuthenticationToken(
                details, null, details.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private Account createAccount(String name, String industry, String bizNo) {
        return Account.builder().name(name).industry(industry).bizNo(bizNo).build();
    }

    @Test
    @DisplayName("matched=true 시 Account의 bizNo, industry가 업데이트된다.")
    void matchedTrue_updatesAccount() {
        Account account = createAccount("삼성SDS", "미분류", null);
        given(accountRepository.findByIdAndAssignedUserIdAndTenantId(ACCOUNT_ID, USER_ID, TENANT_ID))
                .willReturn(Optional.of(account));

        var companyInfo = new CompanyEnrichApiResponse.CompanyInfo(
                "00126186", "삼성에스디에스(주)", "1108128774", "62021",
                "이준희", "서울특별시 송파구", "02-6155-3114", "19850501");
        var data = new CompanyEnrichApiResponse.Data(true, companyInfo, List.of());
        given(companyEnrichClient.enrich(TENANT_ID, "삼성SDS")).willReturn(data);

        AccountEnrichResponse result = accountService.enrich(ACCOUNT_ID);

        assertThat(result.matched()).isTrue();
        assertThat(result.company().bizNo()).isEqualTo("1108128774");
        assertThat(account.getBizNo()).isEqualTo("1108128774");
        assertThat(account.getIndustry()).isEqualTo("62021");
    }

    @Test
    @DisplayName("matched=true이지만 bizNo가 null이면 bizNo는 변경하지 않는다.")
    void matchedTrue_nullBizNo_skipsUpdate() {
        Account account = createAccount("테스트", "미분류", null);
        given(accountRepository.findByIdAndAssignedUserIdAndTenantId(ACCOUNT_ID, USER_ID, TENANT_ID))
                .willReturn(Optional.of(account));

        var companyInfo = new CompanyEnrichApiResponse.CompanyInfo(
                "00000001", "테스트(주)", null, "99999",
                null, null, null, null);
        var data = new CompanyEnrichApiResponse.Data(true, companyInfo, List.of());
        given(companyEnrichClient.enrich(TENANT_ID, "테스트")).willReturn(data);

        accountService.enrich(ACCOUNT_ID);

        assertThat(account.getBizNo()).isNull();
        assertThat(account.getIndustry()).isEqualTo("99999");
    }

    @Test
    @DisplayName("matched=false + candidates 시 Account는 변경하지 않고 후보를 반환한다.")
    void matchedFalse_withCandidates_noAccountUpdate() {
        Account account = createAccount("현대자동", "미분류", null);
        given(accountRepository.findByIdAndAssignedUserIdAndTenantId(ACCOUNT_ID, USER_ID, TENANT_ID))
                .willReturn(Optional.of(account));

        var candidates = List.of(
                new CompanyEnrichApiResponse.CompanyInfo(
                        "00164742", "현대자동차", "1018109147", "30121",
                        "정의선", "서울", "02-1234", "19670601"),
                new CompanyEnrichApiResponse.CompanyInfo(
                        "00164779", "현대건설", "1018122092", "41220",
                        "윤영준", "서울", "02-5678", "19500110")
        );
        var data = new CompanyEnrichApiResponse.Data(false, null, candidates);
        given(companyEnrichClient.enrich(TENANT_ID, "현대자동")).willReturn(data);

        AccountEnrichResponse result = accountService.enrich(ACCOUNT_ID);

        assertThat(result.matched()).isFalse();
        assertThat(result.company()).isNull();
        assertThat(result.candidates()).hasSize(2);
        assertThat(account.getBizNo()).isNull();
        assertThat(account.getIndustry()).isEqualTo("미분류");
    }

    @Test
    @DisplayName("matched=false + 빈 candidates 시 빈 결과를 반환한다.")
    void matchedFalse_noCandidates() {
        Account account = createAccount("없는회사", "미분류", null);
        given(accountRepository.findByIdAndAssignedUserIdAndTenantId(ACCOUNT_ID, USER_ID, TENANT_ID))
                .willReturn(Optional.of(account));

        var data = new CompanyEnrichApiResponse.Data(false, null, List.of());
        given(companyEnrichClient.enrich(TENANT_ID, "없는회사")).willReturn(data);

        AccountEnrichResponse result = accountService.enrich(ACCOUNT_ID);

        assertThat(result.matched()).isFalse();
        assertThat(result.candidates()).isEmpty();
    }

    @Test
    @DisplayName("Account가 없으면 NOT_FOUND 예외가 발생한다.")
    void accountNotFound_throwsNotFound() {
        given(accountRepository.findByIdAndAssignedUserIdAndTenantId(ACCOUNT_ID, USER_ID, TENANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.enrich(ACCOUNT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(CommonErrorCode.NOT_FOUND);

        verify(companyEnrichClient, never()).enrich(any(), any());
    }
}

package com.ssafy.fint.domain.deal.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.service.ContactService;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.repository.DealContactRepository;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.deal.repository.PipelineStageRepository;
import com.ssafy.fint.domain.tenant.repository.TeamRepository;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.DealErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("DealService.softDelete 단위 테스트")
class DealServiceSoftDeleteTest {

    private static final Long USER_ID = 1L;
    private static final Long TENANT_ID = 1L;
    private static final Long DEAL_ID = 42L;

    @Mock private DealRepository dealRepository;

    @Mock @SuppressWarnings("unused") private DealContactRepository dealContactRepository;
    @Mock @SuppressWarnings("unused") private AccountRepository accountRepository;
    @Mock @SuppressWarnings("unused") private ContactService contactService;
    @Mock @SuppressWarnings("unused") private TeamRepository teamRepository;
    @Mock @SuppressWarnings("unused") private UserRepository userRepository;
    @Mock @SuppressWarnings("unused") private PipelineStageRepository pipelineStageRepository;
    @Mock @SuppressWarnings("unused") private ActivityRepository activityRepository;

    @InjectMocks
    private DealService dealService;

    private CustomUserDetails me() {
        return new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");
    }

    @Test
    @DisplayName("정상 soft delete 시 deal 의 isDeleted 가 true 로 변경된다")
    void 정상_softDelete() {
        Deal deal = newDeal();
        given(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .willReturn(Optional.of(deal));

        dealService.softDelete(me(), DEAL_ID);

        assertThat(deal.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("deal 이 존재하지 않으면 DEAL_NOT_FOUND 예외 발생")
    void 딜_없으면_DEAL_NOT_FOUND() {
        given(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> dealService.softDelete(me(), DEAL_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DealErrorCode.DEAL_NOT_FOUND);
    }

    private Deal newDeal() {
        Account account = mock(Account.class);
        Deal deal = Deal.builder()
                .account(account)
                .title("딜")
                .build();
        ReflectionTestUtils.setField(deal, "dealId", DEAL_ID);
        return deal;
    }
}

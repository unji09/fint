package com.ssafy.fint.domain.deal.service;

import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.DealContact;
import com.ssafy.fint.domain.deal.repository.DealContactRepository;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.DealErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("딜-담당자 연결 해제 단위 테스트")
class DealServiceUnlinkContactTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 99L;
    private static final Long DEAL_ID = 10L;
    private static final Long CONTACT_ID = 20L;

    @Mock private DealRepository dealRepository;
    @Mock private DealContactRepository dealContactRepository;

    @InjectMocks
    private DealService dealService;

    private final CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");

    @Test
    @DisplayName("정상 — 딜-담당자 연결을 삭제한다.")
    void unlinkContact_success() {
        Deal deal = mock(Deal.class);
        DealContact dc = mock(DealContact.class);

        when(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .thenReturn(Optional.of(deal));
        when(dealContactRepository.findByDeal_DealIdAndContact_ContactId(DEAL_ID, CONTACT_ID))
                .thenReturn(Optional.of(dc));

        dealService.unlinkContact(me, DEAL_ID, CONTACT_ID);

        verify(dealContactRepository).delete(dc);
    }

    @Test
    @DisplayName("딜이 존재하지 않거나 테넌트 불일치 시 DEAL_NOT_FOUND 예외가 발생한다.")
    void unlinkContact_dealNotFound() {
        when(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> dealService.unlinkContact(me, DEAL_ID, CONTACT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DealErrorCode.DEAL_NOT_FOUND);
    }

    @Test
    @DisplayName("해당 딜-담당자 연결이 없으면 DEAL_CONTACT_NOT_FOUND 예외가 발생한다.")
    void unlinkContact_contactNotLinked() {
        Deal deal = mock(Deal.class);

        when(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .thenReturn(Optional.of(deal));
        when(dealContactRepository.findByDeal_DealIdAndContact_ContactId(DEAL_ID, CONTACT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> dealService.unlinkContact(me, DEAL_ID, CONTACT_ID))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(DealErrorCode.DEAL_CONTACT_NOT_FOUND);
    }
}

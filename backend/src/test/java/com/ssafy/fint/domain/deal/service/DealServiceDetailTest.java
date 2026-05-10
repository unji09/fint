package com.ssafy.fint.domain.deal.service;

import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.service.ContactService;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.dto.DealDetailResponse;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("DealService.findDetail 단위 테스트 (미팅 수 포함)")
class DealServiceDetailTest {

    private static final Long CURRENT_USER_ID = 1L;
    private static final Long CURRENT_TENANT_ID = 1L;
    private static final Long DEAL_ID = 42L;

    @Mock private DealRepository dealRepository;
    @Mock private DealContactRepository dealContactRepository;
    @Mock private ActivityRepository activityRepository;

    @Mock @SuppressWarnings("unused") private AccountRepository accountRepository;
    @Mock @SuppressWarnings("unused") private ContactService contactService;
    @Mock @SuppressWarnings("unused") private TeamRepository teamRepository;
    @Mock @SuppressWarnings("unused") private UserRepository userRepository;
    @Mock @SuppressWarnings("unused") private PipelineStageRepository pipelineStageRepository;

    @InjectMocks
    private DealService dealService;

    private CustomUserDetails me() {
        return new CustomUserDetails(CURRENT_USER_ID, CURRENT_TENANT_ID, "MEMBER");
    }

    private Deal dealMock() {
        Deal deal = mock(Deal.class);
        given(deal.getDealId()).willReturn(DEAL_ID);
        given(deal.getTitle()).willReturn("샘플 딜");
        return deal;
    }

    @Test
    @DisplayName("activityRepository.countMeetingsByDealId 결과가 응답 meetingCount 로 매핑된다")
    void 미팅_수_매핑() {
        Deal deal = dealMock();
        given(dealRepository.findByIdAndTenantId(DEAL_ID, CURRENT_TENANT_ID))
                .willReturn(Optional.of(deal));
        given(activityRepository.countMeetingsByDealId(DEAL_ID)).willReturn(5L);
        given(dealContactRepository.findAllByDealId(DEAL_ID)).willReturn(List.of());

        DealDetailResponse response = dealService.findDetail(me(), DEAL_ID);

        assertThat(response.meetingCount()).isEqualTo(5L);
        verify(activityRepository).countMeetingsByDealId(DEAL_ID);
    }

    @Test
    @DisplayName("딜이 없으면 DEAL_NOT_FOUND 예외가 발생하고 후속 repository 는 호출되지 않는다")
    void 딜_없으면_DEAL_NOT_FOUND() {
        given(dealRepository.findByIdAndTenantId(DEAL_ID, CURRENT_TENANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> dealService.findDetail(me(), DEAL_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DealErrorCode.DEAL_NOT_FOUND);

        verifyNoInteractions(activityRepository);
        verify(dealContactRepository, never()).findAllByDealId(DEAL_ID);
    }
}

package com.ssafy.fint.domain.deal.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.service.ContactService;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.dto.DealUpdateRequest;
import com.ssafy.fint.domain.deal.dto.DealUpdateResponse;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
@DisplayName("DealService.update 단위 테스트")
class DealServiceUpdateTest {

    private static final Long USER_ID = 1L;
    private static final Long TENANT_ID = 1L;
    private static final Long DEAL_ID = 42L;
    private static final Long ACCOUNT_ID = 10L;
    private static final Long PIPELINE_STAGE_ID = 3L;

    @Mock private DealRepository dealRepository;
    @Mock private PipelineStageRepository pipelineStageRepository;

    @Mock @SuppressWarnings("unused") private DealContactRepository dealContactRepository;
    @Mock @SuppressWarnings("unused") private AccountRepository accountRepository;
    @Mock @SuppressWarnings("unused") private ContactService contactService;
    @Mock @SuppressWarnings("unused") private TeamRepository teamRepository;
    @Mock @SuppressWarnings("unused") private UserRepository userRepository;
    @Mock @SuppressWarnings("unused") private ActivityRepository activityRepository;

    @InjectMocks
    private DealService dealService;

    private CustomUserDetails me() {
        return new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");
    }

    @Test
    @DisplayName("title 만 변경하면 title 만 바뀌고 나머지는 유지된다")
    void title_변경() {
        Deal deal = newDeal("원래 제목", new BigDecimal("1000"));
        given(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .willReturn(Optional.of(deal));

        DealUpdateResponse res = dealService.update(me(), DEAL_ID,
                new DealUpdateRequest("새 제목", null, null, null, null, null, null));

        assertThat(res.title()).isEqualTo("새 제목");
        assertThat(res.amount()).isEqualByComparingTo("1000");
    }

    @Test
    @DisplayName("amount 와 expectedClose 동시 변경")
    void amount_expectedClose_변경() {
        Deal deal = newDeal("딜", new BigDecimal("1000"));
        given(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .willReturn(Optional.of(deal));

        LocalDate newClose = LocalDate.of(2026, 12, 31);
        DealUpdateResponse res = dealService.update(me(), DEAL_ID,
                new DealUpdateRequest(null, new BigDecimal("5000"), newClose, null, null, null, null));

        assertThat(res.amount()).isEqualByComparingTo("5000");
        assertThat(res.expectedClose()).isEqualTo(newClose);
        assertThat(res.title()).isEqualTo("딜");
    }

    @Test
    @DisplayName("wonAt 설정 시 markWon 호출 → lostAt/lostReason 클리어")
    void wonAt_설정_시_lost_클리어() {
        Deal deal = newDeal("딜", null);
        deal.markLost(OffsetDateTime.now().minusDays(1), "예산 초과");

        given(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .willReturn(Optional.of(deal));

        OffsetDateTime wonTime = OffsetDateTime.now();
        DealUpdateResponse res = dealService.update(me(), DEAL_ID,
                new DealUpdateRequest(null, null, null, null, wonTime, null, null));

        assertThat(res.wonAt()).isEqualTo(wonTime);
        assertThat(res.lostAt()).isNull();
        assertThat(res.lostReason()).isNull();
    }

    @Test
    @DisplayName("lostAt + lostReason 설정 시 markLost 호출 → wonAt 클리어")
    void lostAt_설정_시_won_클리어() {
        Deal deal = newDeal("딜", null);
        deal.markWon(OffsetDateTime.now().minusDays(1));

        given(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .willReturn(Optional.of(deal));

        OffsetDateTime lostTime = OffsetDateTime.now();
        DealUpdateResponse res = dealService.update(me(), DEAL_ID,
                new DealUpdateRequest(null, null, null, "경쟁사 선택", null, lostTime, null));

        assertThat(res.lostAt()).isEqualTo(lostTime);
        assertThat(res.lostReason()).isEqualTo("경쟁사 선택");
        assertThat(res.wonAt()).isNull();
    }

    @Test
    @DisplayName("lostReason 만 단독 + deal.lostAt 존재 → 기존 lostAt 유지하며 reason 갱신")
    void lostReason만_단독_기존_lostAt_있음() {
        Deal deal = newDeal("딜", null);
        OffsetDateTime existingLostAt = OffsetDateTime.now().minusDays(3);
        deal.markLost(existingLostAt, "기존 사유");

        given(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .willReturn(Optional.of(deal));

        DealUpdateResponse res = dealService.update(me(), DEAL_ID,
                new DealUpdateRequest(null, null, null, "변경된 사유", null, null, null));

        assertThat(res.lostAt()).isEqualTo(existingLostAt);
        assertThat(res.lostReason()).isEqualTo("변경된 사유");
    }

    @Test
    @DisplayName("lostReason 만 단독 + deal.lostAt 없음 → lostReason 무시됨")
    void lostReason만_단독_기존_lostAt_없음() {
        Deal deal = newDeal("딜", null);

        given(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .willReturn(Optional.of(deal));

        DealUpdateResponse res = dealService.update(me(), DEAL_ID,
                new DealUpdateRequest(null, null, null, "사유만 전달", null, null, null));

        assertThat(res.lostAt()).isNull();
        assertThat(res.lostReason()).isNull();
    }

    @Test
    @DisplayName("pipelineStageId 전달 시 PipelineStage 조회 + deal.moveToStage 호출")
    void pipelineStage_변경() {
        Deal deal = newDeal("딜", null);
        PipelineStage stage = mock(PipelineStage.class);
        given(stage.getName()).willReturn("협상");

        given(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .willReturn(Optional.of(deal));
        given(pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(PIPELINE_STAGE_ID, TENANT_ID))
                .willReturn(Optional.of(stage));

        DealUpdateResponse res = dealService.update(me(), DEAL_ID,
                DealUpdateRequest.pipelineStageOnly(PIPELINE_STAGE_ID));

        assertThat(res.currentPipelineStage()).isEqualTo("협상");
    }

    @Test
    @DisplayName("deal 이 존재하지 않으면 DEAL_NOT_FOUND 예외 발생")
    void 딜_없으면_DEAL_NOT_FOUND() {
        given(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> dealService.update(me(), DEAL_ID,
                new DealUpdateRequest("t", null, null, null, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DealErrorCode.DEAL_NOT_FOUND);
    }

    @Test
    @DisplayName("pipelineStageId 가 존재하지 않으면 PIPELINE_STAGE_NOT_FOUND 예외 발생")
    void 파이프라인_없으면_PIPELINE_STAGE_NOT_FOUND() {
        Deal deal = newDeal("딜", null);
        given(dealRepository.findByIdAndTenantId(DEAL_ID, TENANT_ID))
                .willReturn(Optional.of(deal));
        given(pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(PIPELINE_STAGE_ID, TENANT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> dealService.update(me(), DEAL_ID,
                DealUpdateRequest.pipelineStageOnly(PIPELINE_STAGE_ID)))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(DealErrorCode.PIPELINE_STAGE_NOT_FOUND);
    }

    private Deal newDeal(String title, BigDecimal amount) {
        Account account = mock(Account.class);
        lenient().when(account.getAccountId()).thenReturn(ACCOUNT_ID);
        Deal deal = Deal.builder()
                .account(account)
                .title(title)
                .amount(amount)
                .build();
        ReflectionTestUtils.setField(deal, "dealId", DEAL_ID);
        return deal;
    }
}

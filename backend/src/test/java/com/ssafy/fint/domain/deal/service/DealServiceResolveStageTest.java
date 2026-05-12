package com.ssafy.fint.domain.deal.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.dto.DealStageResponse;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.DealErrorCode;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DealServiceResolveStageTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 99L;

    @Mock private DealRepository dealRepository;
    @Mock private ActivityRepository activityRepository;

    @InjectMocks
    private DealService dealService;

    private final CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");

    @Nested
    @DisplayName("resolveCurrentStage — 딜 파이프라인 단계 계산")
    class ResolveCurrentStage {

        @Test
        @DisplayName("활동에서 파이프라인 단계를 찾으면 deal.currentPipeline 을 갱신하고 stageId/stageName 을 반환한다.")
        void resolvesFromLatestActivity() {
            Deal deal = newDeal(10L);
            PipelineStage stage = newStage(3L, "제안");
            Activity activity = newActivity(stage);

            when(activityRepository.findLatestPipelineActivityByDealId(10L))
                    .thenReturn(Optional.of(activity));

            DealStageResponse result = dealService.resolveCurrentStage(deal);

            assertThat(result.stageId()).isEqualTo(3L);
            assertThat(result.stageName()).isEqualTo("제안");
            assertThat(deal.getCurrentPipeline()).isEqualTo("제안");
        }

        @Test
        @DisplayName("해당 딜에 파이프라인 활동이 없으면 null/null 을 반환하고 currentPipeline 을 null 로 초기화한다.")
        void returnsNullWhenNoActivity() {
            Deal deal = newDeal(10L);
            deal.moveToStage("리드");

            when(activityRepository.findLatestPipelineActivityByDealId(10L))
                    .thenReturn(Optional.empty());

            DealStageResponse result = dealService.resolveCurrentStage(deal);

            assertThat(result.stageId()).isNull();
            assertThat(result.stageName()).isNull();
            assertThat(deal.getCurrentPipeline()).isNull();
        }
    }

    @Nested
    @DisplayName("findCurrentStage — GET /deals/{dealId}/stage")
    class FindCurrentStage {

        @Test
        @DisplayName("존재하는 딜의 현재 파이프라인 단계를 반환한다.")
        void returnsStageForExistingDeal() {
            Deal deal = newDeal(10L);
            PipelineStage stage = newStage(3L, "제안");
            Activity activity = newActivity(stage);

            when(dealRepository.findByIdAndTenantId(10L, TENANT_ID))
                    .thenReturn(Optional.of(deal));
            when(activityRepository.findLatestPipelineActivityByDealId(10L))
                    .thenReturn(Optional.of(activity));

            DealStageResponse result = dealService.findCurrentStage(me, 10L);

            assertThat(result.stageId()).isEqualTo(3L);
            assertThat(result.stageName()).isEqualTo("제안");
        }

        @Test
        @DisplayName("존재하지 않는 딜이면 DEAL_NOT_FOUND 예외가 발생한다.")
        void throwsWhenDealNotFound() {
            when(dealRepository.findByIdAndTenantId(999L, TENANT_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> dealService.findCurrentStage(me, 999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(DealErrorCode.DEAL_NOT_FOUND);
        }
    }

    private Deal newDeal(Long dealId) {
        Account account = Account.builder().name("테스트").industry("IT").build();
        ReflectionTestUtils.setField(account, "accountId", 1L);
        Deal deal = Deal.builder().account(account).title("테스트 딜").build();
        ReflectionTestUtils.setField(deal, "dealId", dealId);
        return deal;
    }

    private PipelineStage newStage(Long stageId, String name) {
        Tenant tenant = Tenant.builder().name("t").companyCode("C1").build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);
        PipelineStage stage = PipelineStage.builder()
                .tenant(tenant).name(name).sortOrder(1).build();
        ReflectionTestUtils.setField(stage, "pipelineStageId", stageId);
        return stage;
    }

    private Activity newActivity(PipelineStage stage) {
        Tenant tenant = Tenant.builder().name("t").companyCode("C1").build();
        User user = User.builder()
                .tenant(tenant).role(UserRole.MEMBER)
                .name("owner").passwordHash("x").build();
        Deal deal = newDeal(10L);
        return Activity.builder()
                .user(user).deal(deal).pipelineStage(stage)
                .type(ActivityType.MEETING).title("미팅")
                .startAt(OffsetDateTime.now().minusDays(1))
                .endAt(OffsetDateTime.now())
                .build();
    }
}

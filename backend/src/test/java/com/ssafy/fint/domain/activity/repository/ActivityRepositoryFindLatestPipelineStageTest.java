package com.ssafy.fint.domain.activity.repository;

import com.ssafy.fint.config.TestcontainersConfig;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class ActivityRepositoryFindLatestPipelineStageTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("startAt <= now 이고 pipelineStage 가 있는 활동 중 가장 최근 것을 반환한다.")
    void returnsLatestActivityWithPipelineStage() {
        Tenant tenant = persistTenant();
        User user = persistUser(tenant);
        Account account = persistAccount();
        Deal deal = persistDeal(account);
        PipelineStage lead = persistStage(tenant, "리드", 1);
        PipelineStage proposal = persistStage(tenant, "제안", 2);

        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusDays(3);
        OffsetDateTime recent = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);

        persistActivity(user, deal, lead, past);
        persistActivity(user, deal, proposal, recent);

        Optional<Activity> result = activityRepository.findLatestPipelineActivityByDealId(deal.getDealId());

        assertThat(result).isPresent();
        assertThat(result.get().getPipelineStage().getName()).isEqualTo("제안");
    }

    @Test
    @DisplayName("startAt 이 미래인 활동은 제외된다.")
    void excludesFutureActivities() {
        Tenant tenant = persistTenant();
        User user = persistUser(tenant);
        Account account = persistAccount();
        Deal deal = persistDeal(account);
        PipelineStage lead = persistStage(tenant, "리드", 1);
        PipelineStage proposal = persistStage(tenant, "제안", 2);

        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        OffsetDateTime future = OffsetDateTime.now(ZoneOffset.UTC).plusDays(3);

        persistActivity(user, deal, lead, past);
        persistActivity(user, deal, proposal, future);

        Optional<Activity> result = activityRepository.findLatestPipelineActivityByDealId(deal.getDealId());

        assertThat(result).isPresent();
        assertThat(result.get().getPipelineStage().getName()).isEqualTo("리드");
    }

    @Test
    @DisplayName("pipelineStage 가 null 인 활동은 제외된다.")
    void excludesActivitiesWithoutPipelineStage() {
        Tenant tenant = persistTenant();
        User user = persistUser(tenant);
        Account account = persistAccount();
        Deal deal = persistDeal(account);
        PipelineStage lead = persistStage(tenant, "리드", 1);

        OffsetDateTime recent = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        OffsetDateTime older = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2);

        persistActivity(user, deal, null, recent);
        persistActivity(user, deal, lead, older);

        Optional<Activity> result = activityRepository.findLatestPipelineActivityByDealId(deal.getDealId());

        assertThat(result).isPresent();
        assertThat(result.get().getPipelineStage().getName()).isEqualTo("리드");
    }

    @Test
    @DisplayName("해당 딜에 활동이 없으면 빈 Optional 을 반환한다.")
    void returnsEmptyWhenNoActivities() {
        Tenant tenant = persistTenant();
        Account account = persistAccount();
        Deal deal = persistDeal(account);

        Optional<Activity> result = activityRepository.findLatestPipelineActivityByDealId(deal.getDealId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("다른 딜의 활동은 포함되지 않는다.")
    void doesNotIncludeOtherDealActivities() {
        Tenant tenant = persistTenant();
        User user = persistUser(tenant);
        Account account = persistAccount();
        Deal deal1 = persistDeal(account);
        Deal deal2 = persistDeal(account);
        PipelineStage stage = persistStage(tenant, "제안", 1);

        OffsetDateTime past = OffsetDateTime.now(ZoneOffset.UTC).minusDays(1);
        persistActivity(user, deal2, stage, past);

        Optional<Activity> result = activityRepository.findLatestPipelineActivityByDealId(deal1.getDealId());

        assertThat(result).isEmpty();
    }

    private Tenant persistTenant() {
        Tenant tenant = Tenant.builder().name("test-tenant").companyCode("TT").build();
        em.persist(tenant);
        return tenant;
    }

    private User persistUser(Tenant tenant) {
        User user = User.builder()
                .tenant(tenant)
                .role(UserRole.MEMBER)
                .name("tester")
                .passwordHash("hash")
                .build();
        em.persist(user);
        return user;
    }

    private Account persistAccount() {
        Account account = Account.builder().name("(주)테스트").industry("IT").build();
        em.persist(account);
        return account;
    }

    private Deal persistDeal(Account account) {
        Deal deal = Deal.builder()
                .account(account)
                .title("테스트 딜")
                .build();
        em.persist(deal);
        return deal;
    }

    private PipelineStage persistStage(Tenant tenant, String name, int order) {
        PipelineStage stage = PipelineStage.builder()
                .tenant(tenant)
                .name(name)
                .sortOrder(order)
                .build();
        em.persist(stage);
        return stage;
    }

    private void persistActivity(User user, Deal deal, PipelineStage stage, OffsetDateTime startAt) {
        Activity activity = Activity.builder()
                .user(user)
                .deal(deal)
                .pipelineStage(stage)
                .type(ActivityType.MEETING)
                .title("테스트 활동")
                .startAt(startAt)
                .endAt(startAt.plusHours(1))
                .build();
        em.persist(activity);
        em.flush();
        em.clear();
    }
}

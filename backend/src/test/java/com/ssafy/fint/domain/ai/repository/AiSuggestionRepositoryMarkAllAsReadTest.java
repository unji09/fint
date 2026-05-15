package com.ssafy.fint.domain.ai.repository;

import com.ssafy.fint.config.TestcontainersConfig;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.AccountUserAssignment;
import com.ssafy.fint.domain.ai.entity.AiSuggestion;
import com.ssafy.fint.domain.ai.entity.AiSuggestionRelatedType;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiSuggestionRepository.markAllAsReadByUserId 벌크 업데이트 쿼리 검증.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class AiSuggestionRepositoryMarkAllAsReadTest {

    @Autowired
    private AiSuggestionRepository aiSuggestionRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("미읽은 알림이 여러 건이면 전부 읽음 처리되고 변경 건수를 반환한다.")
    void marksAllUnreadAndReturnsCount() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User user = persistUser(tenant, "user-A");
        Account account = persistAccount("(주)테스트");
        persistAssignment(account, user);
        PipelineStage stage = persistStage(tenant, "리드");
        persistSuggestion(account, stage);
        persistSuggestion(account, stage);
        persistSuggestion(account, stage);

        int count = aiSuggestionRepository.markAllAsReadByUserId(user.getUserId());

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("이미 읽은 알림은 건너뛰고 미읽은 것만 카운트된다.")
    void skipsAlreadyReadSuggestions() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User user = persistUser(tenant, "user-A");
        Account account = persistAccount("(주)테스트");
        persistAssignment(account, user);
        PipelineStage stage = persistStage(tenant, "리드");
        persistSuggestion(account, stage);
        AiSuggestion alreadyRead = persistSuggestion(account, stage);
        alreadyRead.markAsRead();
        em.merge(alreadyRead);
        em.flush();
        em.clear();

        int count = aiSuggestionRepository.markAllAsReadByUserId(user.getUserId());

        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("배정되지 않은 계정의 알림은 변경하지 않는다.")
    void doesNotUpdateUnassignedAccountSuggestions() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User assignee = persistUser(tenant, "assignee");
        User caller = persistUser(tenant, "caller");
        Account account = persistAccount("(주)테스트");
        persistAssignment(account, assignee);
        PipelineStage stage = persistStage(tenant, "리드");
        persistSuggestion(account, stage);

        int count = aiSuggestionRepository.markAllAsReadByUserId(caller.getUserId());

        assertThat(count).isZero();
    }

    @Test
    @DisplayName("미읽은 알림이 없으면 0을 반환한다.")
    void returnsZeroWhenNoneUnread() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User user = persistUser(tenant, "user-A");

        int count = aiSuggestionRepository.markAllAsReadByUserId(user.getUserId());

        assertThat(count).isZero();
    }

    private Tenant persistTenant(String name, String code) {
        Tenant tenant = Tenant.builder().name(name).companyCode(code).build();
        em.persist(tenant);
        return tenant;
    }

    private User persistUser(Tenant tenant, String name) {
        User user = User.builder()
                .tenant(tenant)
                .role(UserRole.MEMBER)
                .name(name)
                .passwordHash("hash")
                .build();
        em.persist(user);
        return user;
    }

    private Account persistAccount(String name) {
        Account account = Account.builder()
                .name(name)
                .industry("IT")
                .build();
        em.persist(account);
        return account;
    }

    private PipelineStage persistStage(Tenant tenant, String name) {
        PipelineStage stage = PipelineStage.builder()
                .tenant(tenant)
                .name(name)
                .sortOrder(1)
                .build();
        em.persist(stage);
        return stage;
    }

    private AccountUserAssignment persistAssignment(Account account, User user) {
        AccountUserAssignment aua = AccountUserAssignment.builder()
                .account(account)
                .user(user)
                .build();
        em.persist(aua);
        return aua;
    }

    private AiSuggestion persistSuggestion(Account account, PipelineStage stage) {
        AiSuggestion suggestion = AiSuggestion.builder()
                .account(account)
                .pipelineStage(stage)
                .title("테스트 알림")
                .content("내용")
                .relatedType(AiSuggestionRelatedType.ACCOUNT)
                .category("GENERAL")
                .successProbability(0)
                .importanceScore(0.0)
                .reason(Map.of("activityType", "미팅"))
                .build();
        em.persist(suggestion);
        em.flush();
        em.clear();
        return suggestion;
    }
}

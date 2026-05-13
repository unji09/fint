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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AiSuggestionRepository.findByIdAndUserId 쿼리 검증.
 * AccountUserAssignment 조인을 통한 접근 권한 격리가 올바르게 동작하는지 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class AiSuggestionRepositoryFindByIdAndUserIdTest {

    @Autowired
    private AiSuggestionRepository aiSuggestionRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("책임자로 배정된 계정의 알림은 조회된다.")
    void findsWhenUserIsAssigned() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User user = persistUser(tenant, "user-A");
        Account account = persistAccount("(주)테스트");
        persistAssignment(account, user);
        PipelineStage stage = persistStage(tenant, "리드");
        AiSuggestion suggestion = persistSuggestion(account, stage);

        Optional<AiSuggestion> result = aiSuggestionRepository
                .findByIdAndUserId(suggestion.getAiSuggestionId(), user.getUserId());

        assertThat(result).isPresent();
        assertThat(result.get().getAiSuggestionId()).isEqualTo(suggestion.getAiSuggestionId());
    }

    @Test
    @DisplayName("책임자로 배정되지 않은 계정의 알림은 조회되지 않는다.")
    void rejectsWhenUserIsNotAssigned() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User assignee = persistUser(tenant, "assignee");
        User caller = persistUser(tenant, "caller");
        Account account = persistAccount("(주)테스트");
        persistAssignment(account, assignee);
        PipelineStage stage = persistStage(tenant, "리드");
        AiSuggestion suggestion = persistSuggestion(account, stage);

        Optional<AiSuggestion> result = aiSuggestionRepository
                .findByIdAndUserId(suggestion.getAiSuggestionId(), caller.getUserId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("삭제된 사용자의 알림은 조회되지 않는다.")
    void rejectsWhenUserIsDeleted() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User user = persistUser(tenant, "user-A");
        Account account = persistAccount("(주)테스트");
        persistAssignment(account, user);
        PipelineStage stage = persistStage(tenant, "리드");
        AiSuggestion suggestion = persistSuggestion(account, stage);

        ReflectionTestUtils.setField(user, "isDeleted", true);
        em.merge(user);
        em.flush();
        em.clear();

        Optional<AiSuggestion> result = aiSuggestionRepository
                .findByIdAndUserId(suggestion.getAiSuggestionId(), user.getUserId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 알림 ID는 빈 결과를 반환한다.")
    void returnsEmptyForNonexistentSuggestion() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User user = persistUser(tenant, "user-A");

        Optional<AiSuggestion> result = aiSuggestionRepository
                .findByIdAndUserId(999L, user.getUserId());

        assertThat(result).isEmpty();
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
                .reason(Map.of("activityType", "미팅"))
                .build();
        em.persist(suggestion);
        em.flush();
        em.clear();
        return suggestion;
    }
}

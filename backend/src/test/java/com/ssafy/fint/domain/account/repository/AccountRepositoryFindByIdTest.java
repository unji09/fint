package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.config.TestcontainersConfig;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.AccountUserAssignment;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AccountRepository.findByIdAndAssignedUserIdAndTenantId 격리 검증 (Account 도메인 권한 검증용).
 * 1) 본인 + 같은 tenant assignment → 조회됨
 * 2) 같은 tenant 다른 user 가 책임자 → 조회 안 됨
 * 3) account 자체는 같지만 호출자가 책임자 아님 → 조회 안 됨
 * 4) 다른 tenant 책임자 → 조회 안 됨
 * Testcontainers PostgreSQL 위에서 JPQL 매핑이 정확한지 함께 확인.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class AccountRepositoryFindByIdTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("본인 + 같은 테넌트 assignment 가 있으면 account 가 조회된다")
    void findsWhenOwnAssignmentInSameTenant() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User caller = persistUser(tenant, "caller");
        Account account = persistAccount();
        persistAssignment(account, caller);

        Optional<Account> result = accountRepository
                .findByIdAndAssignedUserIdAndTenantId(
                        account.getAccountId(), caller.getUserId(), tenant.getTenantId());

        assertThat(result).isPresent();
        assertThat(result.get().getAccountId()).isEqualTo(account.getAccountId());
    }

    @Test
    @DisplayName("호출자가 책임자가 아닌 account 는 조회되지 않는다")
    void rejectsWhenCallerIsNotAssignee() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User assignee = persistUser(tenant, "assignee");
        User caller = persistUser(tenant, "caller");
        Account account = persistAccount();
        persistAssignment(account, assignee);

        Optional<Account> result = accountRepository
                .findByIdAndAssignedUserIdAndTenantId(
                        account.getAccountId(), caller.getUserId(), tenant.getTenantId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("다른 테넌트 책임자만 있으면 조회되지 않는다")
    void rejectsWhenAssignmentInOtherTenant() {
        Tenant tenantA = persistTenant("tenant-A", "TA");
        Tenant tenantB = persistTenant("tenant-B", "TB");
        User assigneeInA = persistUser(tenantA, "assignee-A");
        User callerInB = persistUser(tenantB, "caller-B");
        Account account = persistAccount();
        persistAssignment(account, assigneeInA);

        Optional<Account> result = accountRepository
                .findByIdAndAssignedUserIdAndTenantId(
                        account.getAccountId(), callerInB.getUserId(), tenantB.getTenantId());

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

    private Account persistAccount() {
        Account account = Account.builder()
                .name("(주)테스트")
                .industry("IT")
                .build();
        em.persist(account);
        return account;
    }

    private AccountUserAssignment persistAssignment(Account account, User user) {
        AccountUserAssignment aua = AccountUserAssignment.builder()
                .account(account)
                .user(user)
                .build();
        em.persist(aua);
        em.flush();
        em.clear();
        return aua;
    }
}

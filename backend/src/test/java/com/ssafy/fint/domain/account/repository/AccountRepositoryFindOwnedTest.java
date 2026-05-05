package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.config.TestcontainersConfig;
import com.ssafy.fint.domain.account.entity.Account;
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
 * AccountRepository.findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId 격리 검증.
 * Spring Data 메서드 네이밍이 실제 JPA 매핑과 일치하는지(부팅 시 PropertyReferenceException 미발생),
 * Testcontainers PostgreSQL 위에서 user_id + tenant_id 두 조건이 모두 강제되는지를 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class AccountRepositoryFindOwnedTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("본인 + 같은 테넌트 account 는 조회된다.")
    void findsWhenOwnerAndTenantMatch() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User owner = persistUser(tenant, "owner-A");
        Account account = persistAccount(owner);

        Optional<Account> result = accountRepository
                .findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                        account.getAccountId(), owner.getUserId(), tenant.getTenantId());

        assertThat(result).isPresent();
        assertThat(result.get().getAccountId()).isEqualTo(account.getAccountId());
    }

    @Test
    @DisplayName("같은 테넌트 다른 사용자가 만든 account 는 조회되지 않는다.")
    void rejectsWhenOwnerDiffers() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User ownerOfAccount = persistUser(tenant, "owner-A");
        User caller = persistUser(tenant, "caller-A");
        Account account = persistAccount(ownerOfAccount);

        Optional<Account> result = accountRepository
                .findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                        account.getAccountId(), caller.getUserId(), tenant.getTenantId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("account 의 소유자가 다른 테넌트 소속이면 조회되지 않는다.")
    void rejectsWhenTenantDiffers() {
        Tenant tenantA = persistTenant("tenant-A", "TA");
        Tenant tenantB = persistTenant("tenant-B", "TB");
        User ownerOfA = persistUser(tenantA, "owner-A");
        Account account = persistAccount(ownerOfA);

        Optional<Account> result = accountRepository
                .findByAccountIdAndUser_UserIdAndUser_Tenant_TenantId(
                        account.getAccountId(), ownerOfA.getUserId(), tenantB.getTenantId());

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

    private Account persistAccount(User owner) {
        Account account = Account.builder()
                .user(owner)
                .name("(주)테스트")
                .industry("IT")
                .build();
        em.persist(account);
        em.flush();
        em.clear();
        return account;
    }
}

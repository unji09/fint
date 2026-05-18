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
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AccountRepository.searchInTeam 의 keyword 매칭이 대소문자 무시인지 검증.
 * 회사명은 대소문자 구분이 의미가 없어 "lg" 로 "LG CNS" 가 잡혀야 한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class AccountRepositorySearchInTeamTest {

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("소문자 keyword 로도 대문자 회사명이 매칭된다 (case-insensitive)")
    void matchesUppercaseNameByLowercaseKeyword() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User caller = persistUser(tenant, "caller");
        Account lgCns = persistAccount("LG CNS");
        persistAssignment(lgCns, caller);

        List<Account> result = accountRepository.searchInTeam(
                "lg", null, tenant.getTenantId(), PageRequest.of(0, 10));

        assertThat(result)
                .extracting(Account::getName)
                .containsExactly("LG CNS");
    }

    @Test
    @DisplayName("대문자 keyword 로도 소문자 회사명이 매칭된다 (case-insensitive)")
    void matchesLowercaseNameByUppercaseKeyword() {
        Tenant tenant = persistTenant("tenant-B", "TB");
        User caller = persistUser(tenant, "caller-B");
        Account aws = persistAccount("amazon web services");
        persistAssignment(aws, caller);

        List<Account> result = accountRepository.searchInTeam(
                "AMAZON", null, tenant.getTenantId(), PageRequest.of(0, 10));

        assertThat(result)
                .extracting(Account::getName)
                .containsExactly("amazon web services");
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

package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.config.TestcontainersConfig;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.AccountExternalInfo;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AccountExternalInfoRepository.findRecentByAccountAndOptionalSource 동적 source 필터 검증.
 * 1) source 지정 시 해당 출처만, 2) source = null 이면 모든 출처,
 * 3) Pageable size 가 limit 으로 적용되는지를 Testcontainers PostgreSQL 위에서 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class AccountExternalInfoRepositoryTest {

    @Autowired
    private AccountExternalInfoRepository accountExternalInfoRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("source 지정 시 해당 source 만 occurred_at 내림차순으로 조회된다.")
    void filtersBySource() {
        Account account = persistAccount();
        OffsetDateTime now = OffsetDateTime.now();
        persistInfo(account, "NEWS", "n1", now.minusHours(1));
        persistInfo(account, "NEWS", "n2", now);
        persistInfo(account, "DART", "d1", now.minusMinutes(30));
        em.flush();
        em.clear();

        List<AccountExternalInfo> result = accountExternalInfoRepository
                .findRecentByAccountAndOptionalSource(
                        account.getAccountId(), "NEWS", PageRequest.of(0, 10));

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AccountExternalInfo::getTitle)
                .containsExactly("n2", "n1");
    }

    @Test
    @DisplayName("source 가 null 이면 모든 출처가 occurred_at 내림차순으로 조회된다.")
    void returnsAllSourcesWhenNull() {
        Account account = persistAccount();
        OffsetDateTime now = OffsetDateTime.now();
        persistInfo(account, "NEWS", "n1", now.minusHours(2));
        persistInfo(account, "DART", "d1", now);
        persistInfo(account, "NEWS", "n2", now.minusHours(1));
        em.flush();
        em.clear();

        List<AccountExternalInfo> result = accountExternalInfoRepository
                .findRecentByAccountAndOptionalSource(
                        account.getAccountId(), null, PageRequest.of(0, 10));

        assertThat(result).hasSize(3);
        assertThat(result).extracting(AccountExternalInfo::getTitle)
                .containsExactly("d1", "n2", "n1");
    }

    @Test
    @DisplayName("Pageable 의 size 가 limit 으로 적용되어 N 건만 반환된다.")
    void appliesPageableSizeAsLimit() {
        Account account = persistAccount();
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 0; i < 5; i++) {
            persistInfo(account, "NEWS", "n" + i, now.minusMinutes(i));
        }
        em.flush();
        em.clear();

        List<AccountExternalInfo> result = accountExternalInfoRepository
                .findRecentByAccountAndOptionalSource(
                        account.getAccountId(), null, PageRequest.of(0, 3));

        assertThat(result).hasSize(3);
    }

    private Account persistAccount() {
        Tenant tenant = Tenant.builder().name("t").companyCode("TA").build();
        em.persist(tenant);
        User user = User.builder()
                .tenant(tenant)
                .role(UserRole.MEMBER)
                .name("owner")
                .passwordHash("hash")
                .build();
        em.persist(user);
        Account account = Account.builder()
                .user(user)
                .name("(주)테스트")
                .industry("IT")
                .build();
        em.persist(account);
        return account;
    }

    private AccountExternalInfo persistInfo(Account account, String source, String title, OffsetDateTime occurredAt) {
        AccountExternalInfo info = AccountExternalInfo.builder()
                .account(account)
                .source(source)
                .title(title)
                .content("content")
                .url("https://ex.com")
                .occurredAt(occurredAt)
                .build();
        em.persist(info);
        return info;
    }
}

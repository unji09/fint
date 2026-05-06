package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.config.TestcontainersConfig;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.TemperatureHistory;
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

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TemperatureHistoryRepository.findByAccount_AccountIdOrderByCreatedAtDesc 검증.
 * 1) created_at 내림차순 정렬, 2) 다른 account 격리.
 * Spring Data 메서드 네이밍이 BaseEntity 의 createdAt 필드까지 정상 인식하는지를 함께 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class TemperatureHistoryRepositoryTest {

    @Autowired
    private TemperatureHistoryRepository temperatureHistoryRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("created_at 내림차순(최신순)으로 정렬되어 조회된다.")
    void ordersByCreatedAtDesc() {
        Account account = persistAccount("TA");
        OffsetDateTime now = OffsetDateTime.now();
        persistHistory(account, 60, "초기 온도", now.minusHours(2));
        persistHistory(account, 75, "관심도 상승", now);
        persistHistory(account, 70, "관심도 하락", now.minusHours(1));
        em.flush();
        em.clear();

        List<TemperatureHistory> result = temperatureHistoryRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(account.getAccountId());

        assertThat(result).extracting(TemperatureHistory::getReason)
                .containsExactly("관심도 상승", "관심도 하락", "초기 온도");
    }

    @Test
    @DisplayName("다른 account 의 온도 이력은 조회되지 않는다.")
    void doesNotIncludeOtherAccountHistory() {
        Account accountA = persistAccount("TA");
        Account accountB = persistAccount("TB");
        OffsetDateTime now = OffsetDateTime.now();
        persistHistory(accountA, 60, "A 온도", now);
        persistHistory(accountB, 80, "B 온도", now);
        em.flush();
        em.clear();

        List<TemperatureHistory> result = temperatureHistoryRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(accountA.getAccountId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReason()).isEqualTo("A 온도");
    }

    private Account persistAccount(String companyCode) {
        Tenant tenant = Tenant.builder().name("tenant-" + companyCode).companyCode(companyCode).build();
        em.persist(tenant);
        User user = User.builder()
                .tenant(tenant)
                .role(UserRole.MEMBER)
                .name("owner-" + companyCode)
                .passwordHash("hash")
                .build();
        em.persist(user);
        Account account = Account.builder()
                .user(user)
                .name("(주)테스트-" + companyCode)
                .industry("IT")
                .build();
        em.persist(account);
        return account;
    }

    private TemperatureHistory persistHistory(Account account, int temp, String reason, OffsetDateTime createdAt) {
        TemperatureHistory h = TemperatureHistory.builder()
                .account(account)
                .temperature(temp)
                .reason(reason)
                .build();
        // @CreatedDate 가 null 일 때만 채우는 Spring Data Auditing 특성상,
        // persist 전에 미리 createdAt 을 세팅하면 그대로 유지된다.
        ReflectionTestUtils.setField(h, "createdAt", createdAt);
        em.persist(h);
        return h;
    }
}

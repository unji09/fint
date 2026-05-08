package com.ssafy.fint.domain.account.repository;

import com.ssafy.fint.config.TestcontainersConfig;
import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.Mood;
import com.ssafy.fint.domain.account.entity.TemperatureHistory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TemperatureHistoryRepository.findByAccount_AccountIdOrderByCreatedAtDesc 검증.
 * 1) created_at 내림차순 정렬, 2) 다른 account 격리.
 * mood 컬럼이 enum(VARCHAR) 로 정상 매핑되는지 함께 확인.
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
    @DisplayName("created_at 내림차순(최신순)으로 정렬되어 조회된다")
    void ordersByCreatedAtDesc() {
        Account account = persistAccount("A");
        OffsetDateTime now = OffsetDateTime.now();
        persistHistory(account, Mood.CLOUDY, "초기", now.minusHours(2));
        persistHistory(account, Mood.RAINBOW, "관심도 상승", now);
        persistHistory(account, Mood.SUNNY, "유지", now.minusHours(1));
        em.flush();
        em.clear();

        List<TemperatureHistory> result = temperatureHistoryRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(account.getAccountId());

        assertThat(result).extracting(TemperatureHistory::getReason)
                .containsExactly("관심도 상승", "유지", "초기");
    }

    @Test
    @DisplayName("다른 account 의 mood 이력은 조회되지 않는다")
    void doesNotIncludeOtherAccountHistory() {
        Account accountA = persistAccount("A");
        Account accountB = persistAccount("B");
        OffsetDateTime now = OffsetDateTime.now();
        persistHistory(accountA, Mood.SUNNY, "A mood", now);
        persistHistory(accountB, Mood.RAINY, "B mood", now);
        em.flush();
        em.clear();

        List<TemperatureHistory> result = temperatureHistoryRepository
                .findByAccount_AccountIdOrderByCreatedAtDesc(accountA.getAccountId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getReason()).isEqualTo("A mood");
        assertThat(result.get(0).getMood()).isEqualTo(Mood.SUNNY);
    }

    private Account persistAccount(String suffix) {
        Account account = Account.builder()
                .name("(주)테스트-" + suffix)
                .industry("IT")
                .build();
        em.persist(account);
        return account;
    }

    private TemperatureHistory persistHistory(Account account, Mood mood, String reason, OffsetDateTime createdAt) {
        TemperatureHistory h = TemperatureHistory.builder()
                .account(account)
                .mood(mood)
                .reason(reason)
                .build();
        em.persist(h);
        em.flush();
        // BaseEntity 의 createdAt 은 updatable = false 라 JPA 더티 체킹으로 변경 불가.
        // 정렬 검증을 위해 JPQL UPDATE 로 강제 변경한다.
        em.createQuery(
                        "UPDATE TemperatureHistory th SET th.createdAt = :createdAt "
                                + "WHERE th.temperatureHistoryId = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", h.getTemperatureHistoryId())
                .executeUpdate();
        return h;
    }
}

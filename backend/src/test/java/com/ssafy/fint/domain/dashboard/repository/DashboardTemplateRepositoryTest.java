package com.ssafy.fint.domain.dashboard.repository;

import com.ssafy.fint.config.TestcontainersConfig;
import com.ssafy.fint.domain.dashboard.entity.DashboardTemplate;
import com.ssafy.fint.domain.dashboard.entity.WidgetType;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DashboardTemplateRepository.findByDashboardTemplateIdBetween 검증.
 * 템플릿 그룹 카피(8개 단위) 시 ID 구간 조회가 정확히 동작하는지 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class DashboardTemplateRepositoryTest {

    @Autowired private DashboardTemplateRepository dashboardTemplateRepository;
    @Autowired private EntityManager em;

    @Test
    @DisplayName("startId ~ endId 구간(양 끝 포함)의 template 만 조회된다.")
    void findsBetweenInclusive() {
        DashboardTemplate t1 = persistTemplate("t-1");
        DashboardTemplate t2 = persistTemplate("t-2");
        DashboardTemplate t3 = persistTemplate("t-3");
        em.flush();
        em.clear();

        Long startId = t1.getDashboardTemplateId();
        Long endId = t3.getDashboardTemplateId();

        List<DashboardTemplate> result = dashboardTemplateRepository
                .findByDashboardTemplateIdBetween(startId, endId);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(DashboardTemplate::getDashboardTemplateId)
                .containsExactlyInAnyOrder(t1.getDashboardTemplateId(), t2.getDashboardTemplateId(), t3.getDashboardTemplateId());
    }

    @Test
    @DisplayName("구간을 벗어난 ID 의 template 은 조회되지 않는다.")
    void excludesOutsideRange() {
        DashboardTemplate t1 = persistTemplate("t-1");
        DashboardTemplate t2 = persistTemplate("t-2");
        DashboardTemplate t3 = persistTemplate("t-3");
        em.flush();
        em.clear();

        List<DashboardTemplate> result = dashboardTemplateRepository
                .findByDashboardTemplateIdBetween(t1.getDashboardTemplateId(), t2.getDashboardTemplateId());

        assertThat(result).extracting(DashboardTemplate::getDashboardTemplateId)
                .containsExactlyInAnyOrder(t1.getDashboardTemplateId(), t2.getDashboardTemplateId())
                .doesNotContain(t3.getDashboardTemplateId());
    }

    @Test
    @DisplayName("구간에 해당하는 template 이 없으면 빈 리스트를 반환한다.")
    void returnsEmptyWhenNoMatch() {
        List<DashboardTemplate> result = dashboardTemplateRepository
                .findByDashboardTemplateIdBetween(900_001L, 900_010L);

        assertThat(result).isEmpty();
    }

    private DashboardTemplate persistTemplate(String title) {
        DashboardTemplate template = DashboardTemplate.builder()
                .widgetType(WidgetType.CHART)
                .title(title)
                .config(Map.of("k", "v"))
                .position(Map.of("x", 0, "y", 0, "w", 6, "h", 4))
                .build();
        em.persist(template);
        return template;
    }
}

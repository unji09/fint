package com.ssafy.fint.domain.activity.repository;

import com.ssafy.fint.config.TestcontainersConfig;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * REQ-ACT 도메인 — 활동 상세 조회(findDetail) 의 tenant 격리 검증.
 * Service 단위 테스트는 mock 으로 우회되어 실제 격리 동작을 검증할 수 없으므로,
 * Testcontainers PostgreSQL 위에서 QueryDSL 쿼리가 다른 테넌트 활동을 차단하는지를 직접 확인한다.
 * 격리는 activity.user.tenant.tenantId 단일 경로로 수행된다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class ActivityRepositoryFindDetailTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("같은 테넌트 사용자가 만든 활동은 findDetail 로 조회된다.")
    void findsActivityWhenOwnerBelongsToCurrentTenant() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User owner = persistUser(tenant, "owner-A");
        Activity activity = persistActivity(owner);

        Optional<Activity> result = activityRepository.findDetail(tenant.getTenantId(), activity.getActivityId());

        assertThat(result).isPresent();
        assertThat(result.get().getActivityId()).isEqualTo(activity.getActivityId());
    }

    @Test
    @DisplayName("다른 테넌트 사용자가 만든 활동은 findDetail 로 조회되지 않는다.")
    void rejectsActivityOfAnotherTenant() {
        Tenant tenantA = persistTenant("tenant-A", "TA");
        Tenant tenantB = persistTenant("tenant-B", "TB");
        User ownerOfA = persistUser(tenantA, "owner-A");
        Activity activity = persistActivity(ownerOfA);

        Optional<Activity> result = activityRepository.findDetail(tenantB.getTenantId(), activity.getActivityId());

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

    private Activity persistActivity(User owner) {
        Activity activity = Activity.builder()
                .user(owner)
                .type(ActivityType.MEETING)
                .title("Q2 미팅")
                .startAt(OffsetDateTime.now())
                .endAt(OffsetDateTime.now().plusHours(1))
                .build();
        em.persist(activity);
        em.flush();
        em.clear();
        return activity;
    }
}

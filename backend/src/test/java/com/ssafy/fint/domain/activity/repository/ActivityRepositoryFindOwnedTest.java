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
 * REQ-ACT 도메인 — 활동 단건 삭제 시 사용하는
 * findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId 의 격리 검증.
 * Spring Data 메서드 네이밍이 실제 JPA 매핑과 일치하는지(=부팅 시 PropertyReferenceException 미발생),
 * Testcontainers PostgreSQL 위에서 user_id + tenant_id 두 조건이 모두 강제되는지를 확인한다.
 */
@SpringBootTest
@Import(TestcontainersConfig.class)
@Transactional
class ActivityRepositoryFindOwnedTest {

    @Autowired
    private ActivityRepository activityRepository;

    @Autowired
    private EntityManager em;

    @Test
    @DisplayName("본인 + 같은 테넌트 활동은 조회된다.")
    void findsWhenOwnerAndTenantMatch() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User owner = persistUser(tenant, "owner-A");
        Activity activity = persistActivity(owner);

        Optional<Activity> result = activityRepository
                .findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                        activity.getActivityId(), owner.getUserId(), tenant.getTenantId());

        assertThat(result).isPresent();
        assertThat(result.get().getActivityId()).isEqualTo(activity.getActivityId());
    }

    @Test
    @DisplayName("같은 테넌트 다른 사용자가 만든 활동은 조회되지 않는다.")
    void rejectsWhenOwnerDiffers() {
        Tenant tenant = persistTenant("tenant-A", "TA");
        User ownerOfActivity = persistUser(tenant, "owner-A");
        User caller = persistUser(tenant, "caller-A");
        Activity activity = persistActivity(ownerOfActivity);

        Optional<Activity> result = activityRepository
                .findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                        activity.getActivityId(), caller.getUserId(), tenant.getTenantId());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("activity 의 소유자가 다른 테넌트 소속이면 조회되지 않는다.")
    void rejectsWhenTenantDiffers() {
        Tenant tenantA = persistTenant("tenant-A", "TA");
        Tenant tenantB = persistTenant("tenant-B", "TB");
        User ownerOfA = persistUser(tenantA, "owner-A");
        Activity activity = persistActivity(ownerOfA);

        Optional<Activity> result = activityRepository
                .findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                        activity.getActivityId(), ownerOfA.getUserId(), tenantB.getTenantId());

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

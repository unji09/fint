package com.ssafy.fint.domain.activity.service;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.global.exception.ActivityErrorCode;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * REQ-ACT 도메인 — 활동 단건 삭제(DELETE /activities/{activityId}) 단위 테스트.
 * 본인 소유 + 같은 tenant 활동만 삭제 가능하다는 격리 규칙을 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceDeleteTest {

    private static final Long CURRENT_TENANT_ID = 1L;
    private static final Long CURRENT_USER_ID = 10L;
    private static final Long ACTIVITY_ID = 100L;

    @Mock
    private ActivityRepository activityRepository;

    @InjectMocks
    private ActivityService activityService;

    @BeforeEach
    void setAuthentication() {
        CustomUserDetails principal = new CustomUserDetails(CURRENT_USER_ID, CURRENT_TENANT_ID, "MEMBER");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
        );
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("본인이 만든 활동은 정상적으로 삭제된다.")
    void deleteOwnedActivity() {
        Activity activity = newActivity(ACTIVITY_ID, CURRENT_USER_ID, CURRENT_TENANT_ID);
        when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACTIVITY_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(activity));

        activityService.delete(ACTIVITY_ID);

        verify(activityRepository).delete(activity);
    }

    @Test
    @DisplayName("미존재 활동(또는 타 테넌트·타 사용자 소유)은 ACTIVITY_NOT_FOUND 로 차단된다.")
    void rejectMissingOrForeignActivity() {
        when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACTIVITY_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> activityService.delete(ACTIVITY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ActivityErrorCode.ACTIVITY_NOT_FOUND);

        verify(activityRepository, never()).delete(any(Activity.class));
    }

    @Test
    @DisplayName("Repository 조회 시 현재 사용자·테넌트 ID 가 그대로 전달된다.")
    void passesCurrentUserAndTenantToRepository() {
        Activity activity = newActivity(ACTIVITY_ID, CURRENT_USER_ID, CURRENT_TENANT_ID);
        when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                ACTIVITY_ID, CURRENT_USER_ID, CURRENT_TENANT_ID))
                .thenReturn(Optional.of(activity));

        activityService.delete(ACTIVITY_ID);

        verify(activityRepository)
                .findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                        ACTIVITY_ID, CURRENT_USER_ID, CURRENT_TENANT_ID);
    }

    @Test
    @DisplayName("인증 컨텍스트가 없으면 INVALID_TOKEN 으로 차단된다.")
    void rejectWhenUnauthenticated() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> activityService.delete(ACTIVITY_ID))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.INVALID_TOKEN);

        verifyNoInteractions(activityRepository);
    }

    private Activity newActivity(long activityId, long userId, long tenantId) {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + tenantId).build();
        ReflectionTestUtils.setField(tenant, "tenantId", tenantId);
        User user = User.builder()
                .tenant(tenant)
                .name("tester")
                .passwordHash("x")
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);

        OffsetDateTime now = OffsetDateTime.now();
        Activity activity = Activity.builder()
                .user(user)
                .type(ActivityType.MEETING)
                .title("샘플 활동")
                .startAt(now)
                .endAt(now.plusHours(1))
                .build();
        ReflectionTestUtils.setField(activity, "activityId", activityId);
        return activity;
    }
}

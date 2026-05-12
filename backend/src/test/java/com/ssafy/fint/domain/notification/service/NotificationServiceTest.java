package com.ssafy.fint.domain.notification.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.ai.entity.AiSuggestion;
import com.ssafy.fint.domain.ai.entity.AiSuggestionRelatedType;
import com.ssafy.fint.domain.ai.repository.AiSuggestionRepository;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.notification.dto.NotificationListResponse;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.entity.UserRole;
import com.ssafy.fint.global.security.CustomUserDetails;
import com.ssafy.fint.domain.notification.exception.NotificationErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 알림 목록 조회(GET /notifications) 단위 테스트.
 * 데이터 소스는 ai_suggestions. 로그인 user 가 owner 인 account 의 unread suggestion 최신순 10건.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final Long TENANT_ID = 1L;
    private static final Long USER_ID = 99L;

    @Mock private AiSuggestionRepository aiSuggestionRepository;

    @InjectMocks
    private NotificationService notificationService;

    private final CustomUserDetails me = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");

    @Test
    @DisplayName("정상 조회 시 reason jsonb 의 category/signalSummary/signalTypeBadge 와 account/stage 가 매핑되어 반환된다.")
    void unreadReturnsMappedItems() {
        Account account = newAccount(7L, "(주)삼성전자");
        PipelineStage stage = newStage("제안");
        Map<String, Object> reason = Map.of(
                "activityType", "미팅",
                "signalSummary", "2조 규모 반도체 투자 발표로 구매 활성화 예상",
                "signalTypeBadge", "NEWS"
        );
        AiSuggestion s1 = newSuggestion(account, stage, 1L, "삼성전자 투자 시그널", reason);
        OffsetDateTime createdAt = OffsetDateTime.of(2026, 5, 1, 9, 30, 0, 0, ZoneOffset.UTC);
        ReflectionTestUtils.setField(s1, "createdAt", createdAt);

        when(aiSuggestionRepository.findUnreadByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(s1));

        NotificationListResponse res = notificationService.findUnreadNotifications(me);

        assertThat(res.content()).hasSize(1);
        assertThat(res.content().get(0).notificationId()).isEqualTo(1L);
        assertThat(res.content().get(0).title()).isEqualTo("삼성전자 투자 시그널");
        assertThat(res.content().get(0).category()).isEqualTo("미팅");
        assertThat(res.content().get(0).signalSummary()).startsWith("2조 규모");
        assertThat(res.content().get(0).signalTypeBadge()).isEqualTo("NEWS");
        assertThat(res.content().get(0).pipelineStage()).isEqualTo("제안");
        assertThat(res.content().get(0).accountName()).isEqualTo("(주)삼성전자");
        assertThat(res.content().get(0).isRead()).isFalse();
        assertThat(res.content().get(0).createdAt()).isEqualTo(createdAt);
    }

    @Test
    @DisplayName("Repository 호출 시 PageRequest(0, 10) 가 전달되어 최신 10건으로 제한된다.")
    void unreadLimits10WithPageable() {
        when(aiSuggestionRepository.findUnreadByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());

        notificationService.findUnreadNotifications(me);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(aiSuggestionRepository).findUnreadByUserId(eq(USER_ID), captor.capture());
        Pageable pageable = captor.getValue();
        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(10);
        assertThat(pageable).isEqualTo(PageRequest.of(0, 10));
    }

    @Test
    @DisplayName("결과가 없으면 content 가 빈 배열로 반환된다.")
    void unreadReturnsEmptyContent() {
        when(aiSuggestionRepository.findUnreadByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of());

        NotificationListResponse res = notificationService.findUnreadNotifications(me);

        assertThat(res.content()).isEmpty();
    }

    @Test
    @DisplayName("다건 조회 시 Repository 가 돌려준 정렬 순서가 매핑 후에도 그대로 보존된다.")
    void unreadPreservesRepositoryOrderForMultipleItems() {
        // Repository 쿼리는 createdAt desc 정렬을 보장한다. Service 가 추가 정렬을 끼우지 않음을 명시.
        Account a1 = newAccount(11L, "A사");
        Account a2 = newAccount(12L, "B사");
        Account a3 = newAccount(13L, "C사");
        PipelineStage stage = newStage("리드");

        AiSuggestion newest = newSuggestion(a1, stage, 30L, "최신",
                Map.of("activityType", "미팅"));
        AiSuggestion mid = newSuggestion(a2, stage, 31L, "중간",
                Map.of("activityType", "전화"));
        AiSuggestion oldest = newSuggestion(a3, stage, 32L, "이전",
                Map.of("activityType", "이메일"));

        when(aiSuggestionRepository.findUnreadByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(newest, mid, oldest));

        NotificationListResponse res = notificationService.findUnreadNotifications(me);

        assertThat(res.content()).extracting("notificationId")
                .containsExactly(30L, 31L, 32L);
        assertThat(res.content()).extracting("accountName")
                .containsExactly("A사", "B사", "C사");
        assertThat(res.content()).extracting("category")
                .containsExactly("미팅", "전화", "이메일");
    }

    @Test
    @DisplayName("reason 의 nullable 키(signalSummary/signalTypeBadge)가 없으면 null 로 반환된다.")
    void unreadAllowsNullableSignalFields() {
        Account account = newAccount(8L, "ACME");
        PipelineStage stage = newStage("리드");
        Map<String, Object> reason = new HashMap<>();
        reason.put("activityType", "이메일");
        // signalSummary, signalTypeBadge 누락
        AiSuggestion s = newSuggestion(account, stage, 2L, "메일 회신 필요", reason);

        when(aiSuggestionRepository.findUnreadByUserId(eq(USER_ID), any(Pageable.class)))
                .thenReturn(List.of(s));

        NotificationListResponse res = notificationService.findUnreadNotifications(me);

        assertThat(res.content().get(0).category()).isEqualTo("이메일");
        assertThat(res.content().get(0).signalSummary()).isNull();
        assertThat(res.content().get(0).signalTypeBadge()).isNull();
    }

    private Account newAccount(long accountId, String name) {
        User owner = User.builder()
                .tenant(newTenant())
                .role(UserRole.MEMBER)
                .name("owner")
                .passwordHash("x")
                .build();
        ReflectionTestUtils.setField(owner, "userId", USER_ID);
        Account account = Account.builder()
                .name(name)
                .industry("IT")
                .build();
        ReflectionTestUtils.setField(account, "accountId", accountId);
        return account;
    }

    private Tenant newTenant() {
        Tenant tenant = Tenant.builder().name("t").companyCode("C" + TENANT_ID).build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);
        return tenant;
    }

    private PipelineStage newStage(String name) {
        PipelineStage stage = PipelineStage.builder()
                .tenant(newTenant())
                .name(name)
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(stage, "pipelineStageId", 10L);
        return stage;
    }

    @Nested
    @DisplayName("알림 읽음 처리 (PATCH /notifications/{id}/read)")
    class MarkAsRead {

        @Test
        @DisplayName("정상 — 소유 알림을 읽음 처리하면 isRead 가 true 로 변경된다.")
        void markAsRead_success() {
            AiSuggestion suggestion = newSuggestion(
                    newAccount(7L, "(주)삼성전자"), newStage("제안"), 1L,
                    "삼성전자 투자 시그널", Map.of("activityType", "미팅"));
            assertThat(suggestion.isRead()).isFalse();

            when(aiSuggestionRepository.findByIdAndUserId(1L, USER_ID))
                    .thenReturn(Optional.of(suggestion));

            notificationService.markAsRead(1L, me);

            assertThat(suggestion.isRead()).isTrue();
        }

        @Test
        @DisplayName("알림이 존재하지 않거나 접근 권한이 없으면 NOTIFICATION_NOT_FOUND 예외가 발생한다.")
        void markAsRead_notFound() {
            when(aiSuggestionRepository.findByIdAndUserId(999L, USER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> notificationService.markAsRead(999L, me))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(NotificationErrorCode.NOTIFICATION_NOT_FOUND);
        }

        @Test
        @DisplayName("이미 읽은 알림도 에러 없이 정상 처리된다.")
        void markAsRead_alreadyRead() {
            AiSuggestion suggestion = newSuggestion(
                    newAccount(7L, "(주)삼성전자"), newStage("제안"), 1L,
                    "삼성전자 투자 시그널", Map.of("activityType", "미팅"));
            suggestion.markAsRead();

            when(aiSuggestionRepository.findByIdAndUserId(1L, USER_ID))
                    .thenReturn(Optional.of(suggestion));

            notificationService.markAsRead(1L, me);

            assertThat(suggestion.isRead()).isTrue();
        }
    }

    private AiSuggestion newSuggestion(Account account, PipelineStage stage, long suggestionId,
                                       String title, Map<String, Object> reason) {
        AiSuggestion suggestion = AiSuggestion.builder()
                .account(account)
                .pipelineStage(stage)
                .title(title)
                .content("내용")
                .relatedType(AiSuggestionRelatedType.ACCOUNT)
                .reason(reason)
                .build();
        ReflectionTestUtils.setField(suggestion, "aiSuggestionId", suggestionId);
        return suggestion;
    }
}

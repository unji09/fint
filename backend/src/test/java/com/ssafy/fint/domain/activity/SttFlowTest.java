package com.ssafy.fint.domain.activity;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.activity.client.AiSttClient;
import com.ssafy.fint.domain.activity.dto.RecordingRequest;
import com.ssafy.fint.domain.activity.dto.RecordingResponse;
import com.ssafy.fint.domain.activity.dto.SttCallbackRequest;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.entity.SttStatus;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.activity.service.ActivityService;
import com.ssafy.fint.domain.activity.service.SttCallbackService;
import com.ssafy.fint.domain.activity.service.SttProcessorService;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.deal.repository.PipelineStageRepository;
import com.ssafy.fint.domain.deal.service.DealService;
import com.ssafy.fint.domain.mood.client.MoodClient;
import com.ssafy.fint.domain.tenant.entity.Tenant;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.ActivityErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * REQ-ACT 도메인 — STT 플로우 전체 단위 테스트.
 * <p>
 * Spring @Async HTTP 호출 방식의 STT 플로우를 계층별로 검증한다.
 * <ol>
 *   <li>{@link ActivityService#requestRecording} — PROCESSING 전이 및 SttProcessorService 호출</li>
 *   <li>{@link SttProcessorService} — AiSttClient 호출 및 결과 위임, 실패 처리</li>
 *   <li>{@link SttCallbackService} — transcript 저장 및 MoodClient 호출</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("STT 플로우 단위 테스트")
class SttFlowTest {

    // ── 공통 상수 ──────────────────────────────────────────────────────────────
    private static final Long TENANT_ID   = 1L;
    private static final Long USER_ID     = 10L;
    private static final Long ACTIVITY_ID = 100L;
    private static final Long ACCOUNT_ID  = 42L;
    private static final String FILE_KEY  = "recordings/activity100.webm";

    // ── 공통 헬퍼 ──────────────────────────────────────────────────────────────
    static Activity buildActivity(Long activityId, Deal deal, SttStatus sttStatus) {
        Tenant tenant = Tenant.builder().name("t").companyCode("C1").build();
        ReflectionTestUtils.setField(tenant, "tenantId", TENANT_ID);
        User user = User.builder().tenant(tenant).name("tester").passwordHash("x").build();
        ReflectionTestUtils.setField(user, "userId", USER_ID);

        Activity activity = Activity.builder()
                .user(user)
                .deal(deal)
                .type(ActivityType.MEETING)
                .title("테스트 미팅")
                .startAt(OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.ofHours(9)))
                .endAt(OffsetDateTime.of(2026, 1, 1, 11, 0, 0, 0, ZoneOffset.ofHours(9)))
                .build();
        ReflectionTestUtils.setField(activity, "activityId", activityId);

        if (sttStatus == SttStatus.PROCESSING) {
            activity.changeSttStatus(SttStatus.PROCESSING);
        }
        return activity;
    }

    static Deal buildDeal(Long dealId, Account account) {
        Deal deal = Deal.builder().title("test deal").build();
        ReflectionTestUtils.setField(deal, "dealId", dealId);
        ReflectionTestUtils.setField(deal, "account", account);
        return deal;
    }

    static Account buildAccount(Long accountId) {
        Account account = Account.builder().name("테스트 고객사").industry("IT").build();
        ReflectionTestUtils.setField(account, "accountId", accountId);
        return account;
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 1. ActivityService.requestRecording — PROCESSING 전이 및 SttProcessorService 호출
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("ActivityService — requestRecording")
    class RecordingRequestTests {

        @Mock private ActivityRepository activityRepository;
        @Mock private DealRepository dealRepository;
        @Mock private UserRepository userRepository;
        @Mock private PipelineStageRepository pipelineStageRepository;
        @Mock private DealService dealService;
        @Mock private SttProcessorService sttProcessorService;

        @InjectMocks
        private ActivityService activityService;

        @BeforeEach
        void setAuthentication() {
            CustomUserDetails principal = new CustomUserDetails(USER_ID, TENANT_ID, "MEMBER");
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities())
            );
        }

        @AfterEach
        void clearAuthentication() {
            SecurityContextHolder.clearContext();
        }

        @Test
        @DisplayName("Deal이 있으면 accountId와 함께 SttProcessorService를 호출한다.")
        void requestRecording_withDeal_callsProcessorWithAccountId() {
            Activity activity = buildActivity(ACTIVITY_ID, buildDeal(5L, buildAccount(ACCOUNT_ID)), SttStatus.PENDING);
            when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                    ACTIVITY_ID, USER_ID, TENANT_ID)).thenReturn(Optional.of(activity));

            activityService.requestRecording(ACTIVITY_ID, new RecordingRequest(FILE_KEY, null, null));

            verify(sttProcessorService).process(ACTIVITY_ID, TENANT_ID, ACCOUNT_ID, FILE_KEY, "ko");
        }

        @Test
        @DisplayName("Deal이 없으면 accountId=null로 SttProcessorService를 호출한다.")
        void requestRecording_withoutDeal_callsProcessorWithNullAccountId() {
            Activity activity = buildActivity(ACTIVITY_ID, null, SttStatus.PENDING);
            when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                    ACTIVITY_ID, USER_ID, TENANT_ID)).thenReturn(Optional.of(activity));

            activityService.requestRecording(ACTIVITY_ID, new RecordingRequest(FILE_KEY, null, null));

            verify(sttProcessorService).process(ACTIVITY_ID, TENANT_ID, null, FILE_KEY, "ko");
        }

        @Test
        @DisplayName("sttStatus가 PROCESSING이면 STT_ALREADY_PROCESSING 예외가 발생한다.")
        void requestRecording_alreadyProcessing_throwsException() {
            Activity activity = buildActivity(ACTIVITY_ID, null, SttStatus.PROCESSING);
            when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                    ACTIVITY_ID, USER_ID, TENANT_ID)).thenReturn(Optional.of(activity));

            assertThatThrownBy(() -> activityService.requestRecording(
                    ACTIVITY_ID, new RecordingRequest(FILE_KEY, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ActivityErrorCode.STT_ALREADY_PROCESSING);

            verify(sttProcessorService, never()).process(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("활동이 존재하지 않으면 ACTIVITY_NOT_FOUND 예외가 발생한다.")
        void requestRecording_activityNotFound_throwsException() {
            when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                    ACTIVITY_ID, USER_ID, TENANT_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> activityService.requestRecording(
                    ACTIVITY_ID, new RecordingRequest(FILE_KEY, null, null)))
                    .isInstanceOf(BusinessException.class)
                    .extracting("errorCode")
                    .isEqualTo(ActivityErrorCode.ACTIVITY_NOT_FOUND);

            verify(sttProcessorService, never()).process(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("정상 요청 시 sttStatus가 PROCESSING으로 변경되고 recordingKey가 저장된다.")
        void requestRecording_updatesStatusAndKey() {
            Activity activity = buildActivity(ACTIVITY_ID, null, SttStatus.PENDING);
            when(activityRepository.findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                    ACTIVITY_ID, USER_ID, TENANT_ID)).thenReturn(Optional.of(activity));

            RecordingResponse ignored = activityService.requestRecording(
                    ACTIVITY_ID, new RecordingRequest(FILE_KEY, null, null));

            assertThat(activity.getSttStatus()).isEqualTo(SttStatus.PROCESSING);
            assertThat(activity.getRecordingKey()).isEqualTo(FILE_KEY);
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 2. SttProcessorService — AiSttClient 호출 및 결과 위임, 실패 처리
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("SttProcessorService — @Async 처리")
    class ProcessorServiceTests {

        @Mock private AiSttClient aiSttClient;
        @Mock private SttCallbackService sttCallbackService;

        @InjectMocks
        private SttProcessorService sttProcessorService;

        private static final List<SttCallbackRequest.Segment> SEGMENTS = List.of(
                new SttCallbackRequest.Segment("안녕하세요", "SPEAKER_00", 0, 1000)
        );

        @Test
        @DisplayName("AiSttClient 성공 시 processCallback을 호출한다.")
        void process_success_callsProcessCallback() {
            given(aiSttClient.transcribe(FILE_KEY, TENANT_ID, "ko")).willReturn(SEGMENTS);

            sttProcessorService.process(ACTIVITY_ID, TENANT_ID, ACCOUNT_ID, FILE_KEY, "ko");

            ArgumentCaptor<SttCallbackRequest> captor = ArgumentCaptor.forClass(SttCallbackRequest.class);
            verify(sttCallbackService).processCallback(eq(ACTIVITY_ID), captor.capture());
            assertThat(captor.getValue().tenantId()).isEqualTo(TENANT_ID);
            assertThat(captor.getValue().accountId()).isEqualTo(ACCOUNT_ID);
            assertThat(captor.getValue().segments()).hasSize(1);
        }

        @Test
        @DisplayName("AiSttClient 실패 시 markFailed를 호출하고 processCallback을 호출하지 않는다.")
        void process_aiClientFails_callsMarkFailed() {
            given(aiSttClient.transcribe(FILE_KEY, TENANT_ID, "ko"))
                    .willThrow(new BusinessException(com.ssafy.fint.global.exception.CommonErrorCode.EXTERNAL_API_FAILED));

            sttProcessorService.process(ACTIVITY_ID, TENANT_ID, ACCOUNT_ID, FILE_KEY, "ko");

            verify(sttCallbackService).markFailed(ACTIVITY_ID, TENANT_ID);
            verify(sttCallbackService, never()).processCallback(any(), any());
        }

        @Test
        @DisplayName("accountId=null이어도 processCallback을 호출한다.")
        void process_nullAccountId_callsProcessCallback() {
            given(aiSttClient.transcribe(FILE_KEY, TENANT_ID, "ko")).willReturn(SEGMENTS);

            sttProcessorService.process(ACTIVITY_ID, TENANT_ID, null, FILE_KEY, "ko");

            ArgumentCaptor<SttCallbackRequest> captor = ArgumentCaptor.forClass(SttCallbackRequest.class);
            verify(sttCallbackService).processCallback(eq(ACTIVITY_ID), captor.capture());
            assertThat(captor.getValue().accountId()).isNull();
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // 3. SttCallbackService — transcript 저장 및 MoodClient 호출
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("SttCallbackService — callback 처리")
    class CallbackServiceTests {

        @Mock private ActivityRepository activityRepository;
        @Mock private MoodClient moodClient;

        @InjectMocks
        private SttCallbackService sttCallbackService;

        @Nested
        @DisplayName("정상")
        class Success {

            @Test
            @DisplayName("세그먼트 목록이 transcript에 저장되고 sttStatus가 COMPLETED로 변경된다.")
            void processCallback_updatesTranscriptAndStatus() {
                Activity activity = buildActivity(ACTIVITY_ID, null, SttStatus.PENDING);
                given(activityRepository.findDetail(TENANT_ID, ACTIVITY_ID))
                        .willReturn(Optional.of(activity));

                sttCallbackService.processCallback(ACTIVITY_ID, buildRequest(ACCOUNT_ID, List.of(
                        new SttCallbackRequest.Segment("안녕하세요", "SPEAKER_00", 0, 1000),
                        new SttCallbackRequest.Segment("반갑습니다", "SPEAKER_01", 1500, 2500)
                )));

                assertThat(activity.getSttStatus()).isEqualTo(SttStatus.COMPLETED);
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> segments =
                        (List<Map<String, Object>>) activity.getTranscript().get("segments");
                assertThat(segments).hasSize(2);
                assertThat(segments.get(0))
                        .containsEntry("speaker_id", "SPEAKER_00")
                        .containsEntry("text", "안녕하세요");
            }

            @Test
            @DisplayName("moodClient에 전달되는 transcript는 'speakerId: text' 형식으로 결합된다.")
            void processCallback_buildsMoodTranscriptCorrectly() {
                Activity activity = buildActivity(ACTIVITY_ID, null, SttStatus.PENDING);
                given(activityRepository.findDetail(TENANT_ID, ACTIVITY_ID))
                        .willReturn(Optional.of(activity));

                sttCallbackService.processCallback(ACTIVITY_ID, buildRequest(ACCOUNT_ID, List.of(
                        new SttCallbackRequest.Segment("안녕하세요", "SPEAKER_00", 0, 1000),
                        new SttCallbackRequest.Segment("반갑습니다", "SPEAKER_01", 1500, 2500)
                )));

                ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
                verify(moodClient).requestMoodAnalysis(eq(ACTIVITY_ID), eq(ACCOUNT_ID), captor.capture());
                assertThat(captor.getValue())
                        .contains("SPEAKER_00: 안녕하세요")
                        .contains("SPEAKER_01: 반갑습니다");
            }

            @Test
            @DisplayName("accountId가 null이어도 moodClient를 호출한다.")
            void processCallback_nullAccountId_stillCallsMoodClient() {
                Activity activity = buildActivity(ACTIVITY_ID, null, SttStatus.PENDING);
                given(activityRepository.findDetail(TENANT_ID, ACTIVITY_ID))
                        .willReturn(Optional.of(activity));

                sttCallbackService.processCallback(ACTIVITY_ID, buildRequest(null, List.of(
                        new SttCallbackRequest.Segment("텍스트", "SPEAKER_00", 0, 500)
                )));

                verify(moodClient).requestMoodAnalysis(ACTIVITY_ID, null, "SPEAKER_00: 텍스트");
            }

            @Test
            @DisplayName("세그먼트가 없으면 빈 transcript로 완료된다.")
            void processCallback_emptySegments_completesWithEmptyTranscript() {
                Activity activity = buildActivity(ACTIVITY_ID, null, SttStatus.PENDING);
                given(activityRepository.findDetail(TENANT_ID, ACTIVITY_ID))
                        .willReturn(Optional.of(activity));

                sttCallbackService.processCallback(ACTIVITY_ID, buildRequest(ACCOUNT_ID, List.of()));

                assertThat(activity.getSttStatus()).isEqualTo(SttStatus.COMPLETED);
                @SuppressWarnings("unchecked")
                List<?> segments = (List<?>) activity.getTranscript().get("segments");
                assertThat(segments).isEmpty();
                verify(moodClient).requestMoodAnalysis(ACTIVITY_ID, ACCOUNT_ID, "");
            }
        }

        @Nested
        @DisplayName("예외")
        class Failure {

            @Test
            @DisplayName("Activity를 찾지 못하면 ACTIVITY_NOT_FOUND를 던지고 moodClient를 호출하지 않는다.")
            void processCallback_activityNotFound_throwsException() {
                given(activityRepository.findDetail(TENANT_ID, ACTIVITY_ID))
                        .willReturn(Optional.empty());

                assertThatThrownBy(() -> sttCallbackService.processCallback(
                        ACTIVITY_ID, buildRequest(ACCOUNT_ID, List.of())))
                        .isInstanceOf(BusinessException.class)
                        .extracting("errorCode")
                        .isEqualTo(ActivityErrorCode.ACTIVITY_NOT_FOUND);

                verify(moodClient, never()).requestMoodAnalysis(any(), any(), any());
            }

            @Test
            @DisplayName("markFailed는 Activity를 찾으면 sttStatus를 FAILED로 변경한다.")
            void markFailed_updatesStatusToFailed() {
                Activity activity = buildActivity(ACTIVITY_ID, null, SttStatus.PROCESSING);
                given(activityRepository.findDetail(TENANT_ID, ACTIVITY_ID))
                        .willReturn(Optional.of(activity));

                sttCallbackService.markFailed(ACTIVITY_ID, TENANT_ID);

                assertThat(activity.getSttStatus()).isEqualTo(SttStatus.FAILED);
            }
        }

        private SttCallbackRequest buildRequest(Long accountId, List<SttCallbackRequest.Segment> segments) {
            return new SttCallbackRequest(TENANT_ID, accountId, segments);
        }
    }
}

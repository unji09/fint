package com.ssafy.fint.domain.mood.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.Mood;
import com.ssafy.fint.domain.account.entity.TemperatureHistory;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.TemperatureHistoryRepository;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.ai.dto.NextActionCreateRequest;
import com.ssafy.fint.domain.ai.entity.TriggerType;
import com.ssafy.fint.domain.ai.service.AiSuggestionService;
import com.ssafy.fint.domain.mood.MoodStatus;
import com.ssafy.fint.domain.mood.dto.MoodAnalysisResponse;
import com.ssafy.fint.domain.mood.dto.MoodCallbackRequest;
import com.ssafy.fint.global.exception.AccountErrorCode;
import com.ssafy.fint.global.exception.ActivityErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MoodAnalysisService {

    private final ActivityRepository activityRepository;
    private final TemperatureHistoryRepository temperatureHistoryRepository;
    private final AccountRepository accountRepository;
    private final AiSuggestionService aiSuggestionService;

    public MoodAnalysisResponse getMoodAnalysis(Long activityId) {
        Long tenantId = SecurityUtils.currentTenantId();

        Activity activity = activityRepository.findDetail(tenantId, activityId)
            .orElseThrow(() -> new BusinessException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        MoodStatus moodStatus = activity.getMoodStatus();

        return switch (moodStatus) {
            case PENDING, PROCESSING -> MoodAnalysisResponse.pending(activityId, moodStatus);
            case COMPLETED -> temperatureHistoryRepository
                .findTopByActivity_ActivityIdOrderByCreatedAtDesc(activityId)
                .map(history -> MoodAnalysisResponse.from(activityId, history))
                .orElse(MoodAnalysisResponse.failed(activityId));
            case FAILED ->  MoodAnalysisResponse.failed(activityId);
        };
    }

    @Transactional
    public void processCallback(Long activityId, MoodCallbackRequest request, Long tenantId) {

        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new BusinessException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        Account account = accountRepository.findById(request.accountId())
            .orElseThrow(() -> new BusinessException(AccountErrorCode.ACCOUNT_NOT_FOUND));

        Mood mood = Mood.from(request.moodScore());

        TemperatureHistory history = TemperatureHistory.builder()
            .account(account)
            .activity(activity)
            .mood(mood)
            .moodScore(request.moodScore())
            .reason(request.reason())
            .keySignals(request.keySignals())
            .build();

        temperatureHistoryRepository.save(history);
        activity.updateSummary(request.summary());
        activity.changeMoodStatus(MoodStatus.COMPLETED);

        log.info("[MoodCallback] activityId={} mood={} score={}", activityId, mood, request.moodScore());

        triggerNextAction(tenantId, request.accountId(), activityId);
    }

    private void triggerNextAction(Long tenantId, Long accountId, Long activityId) {
        if (tenantId == null) {
            log.warn("[MoodCallback] tenantId is null, skipping next action trigger. activityId={}", activityId);
            return;
        }
        try {
            NextActionCreateRequest request = new NextActionCreateRequest(
                    accountId, TriggerType.MEETING_CREATED,
                    List.of(), List.of(), List.of(activityId), null);
            aiSuggestionService.createNextActionBySystem(tenantId, request);
            log.info("[MoodCallback] next action triggered. tenantId={} accountId={} activityId={}",
                    tenantId, accountId, activityId);
        } catch (Exception e) {
            log.error("[MoodCallback] next action trigger failed. accountId={} activityId={}",
                    accountId, activityId, e);
        }
    }

    @Transactional
    public void markFailed(Long activityId, String reason) {
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new BusinessException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        activity.changeMoodStatus(MoodStatus.FAILED);

        log.warn("[MoodCallback] markFailed activityId={} reason={}", activityId, reason);
    }
}

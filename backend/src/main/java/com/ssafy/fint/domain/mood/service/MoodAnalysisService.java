package com.ssafy.fint.domain.mood.service;

import com.ssafy.fint.domain.account.entity.Account;
import com.ssafy.fint.domain.account.entity.Mood;
import com.ssafy.fint.domain.account.entity.TemperatureHistory;
import com.ssafy.fint.domain.account.repository.AccountRepository;
import com.ssafy.fint.domain.account.repository.TemperatureHistoryRepository;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
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

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MoodAnalysisService {

    private final ActivityRepository activityRepository;
    private final TemperatureHistoryRepository temperatureHistoryRepository;
    private final AccountRepository accountRepository;

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
    public void processCallback(Long activityId, MoodCallbackRequest request) {

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
        activity.changeMoodStatus(MoodStatus.COMPLETED);

        log.info("[MoodCallback] activityId={} mood={} score={}", activityId, mood, request.moodScore());
    }
}

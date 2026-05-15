package com.ssafy.fint.domain.activity.service;

import com.ssafy.fint.domain.activity.dto.SttCallbackRequest;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.SttStatus;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.mood.client.MoodClient;
import com.ssafy.fint.global.exception.ActivityErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SttCallbackService {

    private final ActivityRepository activityRepository;
    private final MoodClient moodClient;

    @Transactional
    public void processCallback(Long activityId, SttCallbackRequest request) {
        Activity activity = activityRepository.findDetail(request.tenantId(), activityId)
                .orElseThrow(() -> new BusinessException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        List<Map<String, Object>> segments = request.segments().stream()
                .map(s -> Map.<String, Object>of(
                        "speaker_id", s.speakerId(),
                        "text", s.text(),
                        "start_ms", s.startMs(),
                        "end_ms", s.endMs()
                ))
                .collect(Collectors.toList());

        activity.updateTranscript(Map.of("segments", segments));
        activity.changeSttStatus(SttStatus.COMPLETED);

        String transcript = request.segments().stream()
                .map(s -> s.speakerId() + ": " + s.text())
                .collect(Collectors.joining("\n"));

        moodClient.requestMoodAnalysis(activityId, request.accountId(), transcript);

        log.info("[SttCallback] 처리 완료 activityId={} tenantId={} segments={}",
                activityId, request.tenantId(), segments.size());
    }

    @Transactional
    public void markFailed(Long activityId, Long tenantId) {
        activityRepository.findDetail(tenantId, activityId)
                .ifPresent(a -> a.changeSttStatus(SttStatus.FAILED));
        log.warn("[SttCallback] STT 실패 처리 activityId={} tenantId={}", activityId, tenantId);
    }
}

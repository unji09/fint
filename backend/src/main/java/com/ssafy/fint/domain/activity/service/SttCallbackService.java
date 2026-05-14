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
        Activity activity = activityRepository.findById(activityId)
            .orElseThrow(() -> new BusinessException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        List<Map<String, Object>> segments = request.segments().stream()
            .map(s -> Map.<String, Object>of(
                "speakerId", s.speakerId(),
                "text", s.text(),
                "startMs", s.startMs(),
                "endMs", s.endMs()
            ))
            .collect(Collectors.toList());

        activity.updateTranscript(Map.of("segments", segments));
        activity.changeSttStatus(SttStatus.COMPLETED);

        // 전체 텍스트 조합 후 날씨 분석 요청
        String transcript = request.segments().stream()
            .map(s -> s.speakerId() + ": " + s.text())
            .collect(Collectors.joining("\n"));

        moodClient.requestMoodAnalysis(activityId, request.accountId(), transcript);

        log.info("[SttCallback] 처리 완료 activityId={} segments={}", activityId, segments.size());
    }
}

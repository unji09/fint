package com.ssafy.fint.domain.activity.service;

import com.ssafy.fint.domain.activity.dto.SttCallbackRequest;
import com.ssafy.fint.domain.activity.entity.Recording;
import com.ssafy.fint.domain.activity.entity.SttStatus;
import com.ssafy.fint.domain.activity.repository.RecordingRepository;
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

    private final RecordingRepository recordingRepository;
    private final MoodClient moodClient;

    @Transactional
    public void processCallback(Long recordingId, Long activityId, SttCallbackRequest request) {
        Recording recording = recordingRepository.findByIdAndTenantId(recordingId, request.tenantId())
                .orElseThrow(() -> new BusinessException(ActivityErrorCode.RECORDING_NOT_FOUND));

        List<Map<String, Object>> segments = request.segments().stream()
                .map(s -> Map.<String, Object>of(
                        "speaker_id", s.speakerId(),
                        "text", s.text(),
                        "start_ms", s.startMs(),
                        "end_ms", s.endMs()
                ))
                .collect(Collectors.toList());

        recording.updateTranscript(Map.of("segments", segments));
        recording.changeSttStatus(SttStatus.COMPLETED);

        String transcript = request.segments().stream()
                .map(s -> s.speakerId() + ": " + s.text())
                .collect(Collectors.joining("\n"));

        moodClient.requestMoodAnalysis(activityId, request.accountId(), transcript);

        log.info("[SttCallback] 처리 완료 recordingId={} activityId={} tenantId={} segments={}",
                recordingId, activityId, request.tenantId(), segments.size());
    }

    @Transactional
    public void markFailed(Long recordingId, Long tenantId) {
        recordingRepository.findByIdAndTenantId(recordingId, tenantId)
                .ifPresent(r -> r.changeSttStatus(SttStatus.FAILED));
        log.warn("[SttCallback] STT 실패 처리 recordingId={} tenantId={}", recordingId, tenantId);
    }
}

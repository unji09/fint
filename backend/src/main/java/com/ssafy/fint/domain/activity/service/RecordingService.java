package com.ssafy.fint.domain.activity.service;

import com.ssafy.fint.domain.activity.dto.RecordingListResponse;
import com.ssafy.fint.domain.activity.dto.RecordingRequest;
import com.ssafy.fint.domain.activity.dto.RecordingResponse;
import com.ssafy.fint.domain.activity.dto.RecordingUpdateRequest;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.Recording;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.activity.repository.RecordingRepository;
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
public class RecordingService {

    private final RecordingRepository recordingRepository;
    private final ActivityRepository activityRepository;
    private final SttProcessorService sttProcessorService;

    public RecordingListResponse list(Long activityId) {
        Long tenantId = SecurityUtils.currentTenantId();
        List<Recording> recordings =
                recordingRepository.findByActivity_ActivityIdAndTenantIdOrderByCreatedAtDesc(activityId, tenantId);
        return RecordingListResponse.from(recordings);
    }

    @Transactional
    public RecordingResponse create(Long activityId, RecordingRequest request) {
        Long tenantId = SecurityUtils.currentTenantId();
        Long userId = SecurityUtils.currentUserId();

        Activity activity = activityRepository
                .findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(activityId, userId, tenantId)
                .orElseThrow(() -> new BusinessException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        Recording recording = Recording.builder()
                .activity(activity)
                .tenantId(tenantId)
                .fileKey(request.fileKey())
                .title(request.title() != null ? request.title() : activity.getTitle())
                .duration(request.duration() != null ? request.duration() : 0)
                .build();

        Recording saved = recordingRepository.save(recording);

        Long accountId = activity.getDeal() != null && activity.getDeal().getAccount() != null
                ? activity.getDeal().getAccount().getAccountId()
                : null;

        sttProcessorService.process(activityId, saved.getRecordingId(), tenantId, accountId, request.fileKey(), "ko");

        log.info("[Recording] created activityId={} recordingId={} fileKey={}", activityId, saved.getRecordingId(), request.fileKey());

        return RecordingResponse.from(saved);
    }

    @Transactional
    public RecordingResponse updateTitle(Long activityId, Long recordingId, RecordingUpdateRequest request) {
        Long tenantId = SecurityUtils.currentTenantId();

        Recording recording = recordingRepository.findByIdAndTenantId(recordingId, tenantId)
                .orElseThrow(() -> new BusinessException(ActivityErrorCode.RECORDING_NOT_FOUND));

        if (!recording.getActivity().getActivityId().equals(activityId)) {
            throw new BusinessException(ActivityErrorCode.RECORDING_NOT_FOUND);
        }

        recording.updateTitle(request.title());
        log.info("[Recording] title updated recordingId={} title={}", recordingId, request.title());

        return RecordingResponse.from(recording);
    }

    @Transactional
    public void delete(Long activityId, Long recordingId) {
        Long tenantId = SecurityUtils.currentTenantId();

        Recording recording = recordingRepository.findByIdAndTenantId(recordingId, tenantId)
                .orElseThrow(() -> new BusinessException(ActivityErrorCode.RECORDING_NOT_FOUND));

        if (!recording.getActivity().getActivityId().equals(activityId)) {
            throw new BusinessException(ActivityErrorCode.RECORDING_NOT_FOUND);
        }

        recordingRepository.delete(recording);
        log.info("[Recording] deleted recordingId={} activityId={} tenantId={}", recordingId, activityId, tenantId);
    }
}

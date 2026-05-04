package com.ssafy.fint.domain.activity.service;

import com.ssafy.fint.domain.activity.dto.ActivityCreateRequest;
import com.ssafy.fint.domain.activity.dto.ActivityCreateResponse;
import com.ssafy.fint.domain.activity.dto.ActivityDetailResponse;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.deal.repository.PipelineStageRepository;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.ActivityErrorCode;
import com.ssafy.fint.global.exception.AuthErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final DealRepository dealRepository;
    private final PipelineStageRepository pipelineStageRepository;
    private final UserRepository userRepository;

    public Page<Activity> findAll(ActivityListFilter filter, Pageable pageable) {
        return activityRepository.search(filter, pageable);
    }

    public ActivityDetailResponse findDetail(Long activityId, Long dealIdFilter) {
        Long tenantId = currentTenantId();

        Activity activity = activityRepository.findDetail(activityId)
                .orElseThrow(() -> {
                    log.debug("[ActivityDetail] not found. activityId={} tenantId={} dealIdFilter={}",
                            activityId, tenantId, dealIdFilter);
                    return new BusinessException(ActivityErrorCode.ACTIVITY_NOT_FOUND);
                });

        if (dealIdFilter != null) {
            Long activityDealId = activity.getDeal() == null ? null : activity.getDeal().getDealId();
            if (!dealIdFilter.equals(activityDealId)) {
                log.debug("[ActivityDetail] dealId mismatch. activityId={} tenantId={} dealIdFilter={} actualDealId={}",
                        activityId, tenantId, dealIdFilter, activityDealId);
                throw new BusinessException(ActivityErrorCode.ACTIVITY_NOT_FOUND);
            }
        }

        return ActivityDetailResponse.from(activity);
    }

    @Transactional
    public ActivityCreateResponse create(ActivityCreateRequest request) {
        if (request.endAt().isBefore(request.startAt())) {
            throw new BusinessException(ActivityErrorCode.INVALID_TIME_RANGE);
        }

        Long tenantId = currentTenantId();
        Long userId = currentUserId();

        Deal deal = null;
        if (request.dealId() != null) {
            deal = dealRepository.findByIdAndTenantId(request.dealId(), tenantId)
                    .orElseThrow(() -> new BusinessException(ActivityErrorCode.DEAL_NOT_FOUND));
        }

        PipelineStage stage = null;
        if (request.pipelineStageId() != null) {
            stage = pipelineStageRepository.findByPipelineStageIdAndTenant_TenantId(request.pipelineStageId(), tenantId)
                    .orElseThrow(() -> new BusinessException(ActivityErrorCode.PIPELINE_STAGE_NOT_FOUND));
        }

        User owner = userRepository.getReferenceById(userId);

        Activity activity = Activity.builder()
                .user(owner)
                .deal(deal)
                .pipelineStage(stage)
                .type(request.type())
                .title(request.title())
                .startAt(request.startAt())
                .endAt(request.endAt())
                .attendees(request.attendees())
                .memo(request.memo())
                .build();

        Activity saved = activityRepository.save(activity);
        log.debug("[ActivityCreate] activityId={} tenantId={} userId={} dealId={} pipelineStageId={}",
                saved.getActivityId(), tenantId, userId, request.dealId(), request.pipelineStageId());
        return ActivityCreateResponse.from(saved);
    }

    private Long currentTenantId() {
        return currentPrincipal().getTenantId();
    }

    private Long currentUserId() {
        return currentPrincipal().getUserId();
    }

    private CustomUserDetails currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CustomUserDetails me)) {
            throw new BusinessException(AuthErrorCode.INVALID_TOKEN);
        }
        return me;
    }
}

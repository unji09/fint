package com.ssafy.fint.domain.activity.service;

import com.ssafy.fint.domain.activity.dto.ActivityCreateRequest;
import com.ssafy.fint.domain.activity.dto.ActivityCreateResponse;
import com.ssafy.fint.domain.activity.dto.ActivityDetailResponse;
import com.ssafy.fint.domain.activity.dto.ActivityUpdateRequest;
import com.ssafy.fint.domain.activity.dto.ActivityUpdateResponse;
import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.deal.dto.DealCreateResponse;
import com.ssafy.fint.domain.deal.dto.DealUpdateRequest;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.deal.entity.PipelineStage;
import com.ssafy.fint.domain.deal.repository.DealRepository;
import com.ssafy.fint.domain.deal.repository.PipelineStageRepository;
import com.ssafy.fint.domain.deal.service.DealService;
import com.ssafy.fint.domain.user.entity.User;
import com.ssafy.fint.domain.user.repository.UserRepository;
import com.ssafy.fint.global.exception.ActivityErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CommonErrorCode;
import com.ssafy.fint.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;

import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
    private final DealService dealService;

    public Page<Activity> findAll(ActivityListFilter filter, Pageable pageable) {
        return activityRepository.search(SecurityUtils.currentTenantId(), filter, pageable);
    }

    public ActivityDetailResponse findDetail(Long activityId, Long dealIdFilter) {
        Long tenantId = SecurityUtils.currentTenantId();

        Activity activity = activityRepository.findDetail(tenantId, activityId)
                .orElseThrow(() -> {
                    log.debug("[ActivityDetail] not found. activityId={} tenantId={} dealIdFilter={}",
                            activityId, tenantId, dealIdFilter);
                    return activityNotFound();
                });

        if (dealIdFilter != null) {
            Long activityDealId = activity.getDeal() == null ? null : activity.getDeal().getDealId();
            if (!dealIdFilter.equals(activityDealId)) {
                log.debug("[ActivityDetail] dealId mismatch. activityId={} tenantId={} dealIdFilter={} actualDealId={}",
                        activityId, tenantId, dealIdFilter, activityDealId);
                throw activityNotFound();
            }
        }

        return ActivityDetailResponse.from(activity);
    }

    @Transactional
    public ActivityCreateResponse create(ActivityCreateRequest request) {
        if (request.endAt().isBefore(request.startAt())) {
            throw new BusinessException(ActivityErrorCode.INVALID_TIME_RANGE);
        }

        Long tenantId = SecurityUtils.currentTenantId();
        Long userId = SecurityUtils.currentUserId();

        Deal deal = null;
        if (request.dealId() != null) {
            deal = dealRepository.findByIdAndTenantId(request.dealId(), tenantId)
                    .orElseThrow(() -> new BusinessException(ActivityErrorCode.DEAL_NOT_FOUND));
        } else if (request.newDeal() != null) {
            DealCreateResponse created = dealService.create(SecurityUtils.currentPrincipal(), request.newDeal());
            deal = dealRepository.getReferenceById(created.dealId());
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

        if (request.dealId() != null && request.pipelineStageId() != null) {
            dealService.update(
                    SecurityUtils.currentPrincipal(),
                    request.dealId(),
                    DealUpdateRequest.pipelineStageOnly(request.pipelineStageId())
            );
        }

        log.debug("[ActivityCreate] activityId={} tenantId={} userId={} dealId={} newDeal={} pipelineStageId={}",
                saved.getActivityId(), tenantId, userId, request.dealId(),
                request.newDeal() != null, request.pipelineStageId());
        return ActivityCreateResponse.from(saved);
    }

    @Transactional
    public void delete(Long activityId) {
        Long tenantId = SecurityUtils.currentTenantId();
        Long userId = SecurityUtils.currentUserId();

        Activity activity = activityRepository
                .findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(activityId, userId, tenantId)
                .orElseThrow(() -> {
                    log.debug("[ActivityDelete] not found. activityId={} userId={} tenantId={}",
                            activityId, userId, tenantId);
                    return activityNotFound();
                });

        activityRepository.delete(activity);
        log.info("[ActivityDelete] activityId={} userId={} tenantId={}", activityId, userId, tenantId);
    }

    private BusinessException activityNotFound() {
        return new BusinessException(ActivityErrorCode.ACTIVITY_NOT_FOUND);
    }

    @Transactional
    public ActivityUpdateResponse update(Long activityId, ActivityUpdateRequest request) {
        Long tenantId = SecurityUtils.currentTenantId();
        Long userId = SecurityUtils.currentUserId();

        Activity activity = activityRepository.findDetail(tenantId, activityId)
                .orElseThrow(() -> new BusinessException(ActivityErrorCode.ACTIVITY_NOT_FOUND));
        if (!activity.getUser().getUserId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }

        if (request.title() != null && request.title().isBlank()) {
            throw new BusinessException(ActivityErrorCode.BLANK_TITLE);
        }
        OffsetDateTime start = request.startAt() != null ? request.startAt() : activity.getStartAt();
        OffsetDateTime end = request.endAt() != null ? request.endAt() : activity.getEndAt();
        if (end.isBefore(start)) {
            throw new BusinessException(ActivityErrorCode.INVALID_TIME_RANGE);
        }

        if (request.dealId() != null) {
            activity.changeDeal(dealRepository.findByIdAndTenantId(request.dealId(), tenantId)
                    .orElseThrow(() -> new BusinessException(ActivityErrorCode.DEAL_NOT_FOUND)));
        }
        if (request.pipelineStageId() != null) {
            activity.changePipelineStage(pipelineStageRepository
                    .findByPipelineStageIdAndTenant_TenantId(request.pipelineStageId(), tenantId)
                    .orElseThrow(() -> new BusinessException(ActivityErrorCode.PIPELINE_STAGE_NOT_FOUND)));
        }
        if (request.type() != null) activity.changeType(request.type());
        if (request.title() != null) activity.changeTitle(request.title());
        if (request.startAt() != null || request.endAt() != null) {
            activity.changeSchedule(start, end);
        }
        if (request.attendees() != null) activity.changeAttendees(request.attendees());
        if (request.memo() != null) activity.changeMemo(request.memo());

        Activity saved = activityRepository.saveAndFlush(activity);
        log.debug("[ActivityUpdate] activityId={} tenantId={} userId={}", saved.getActivityId(), tenantId, userId);
        return ActivityUpdateResponse.from(saved);
    }
}

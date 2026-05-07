package com.ssafy.fint.domain.calendar.service;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.calendar.CalendarEventConstants;
import com.ssafy.fint.domain.calendar.dto.CalendarEventDetailResponse;
import com.ssafy.fint.domain.calendar.dto.CalendarEventListResponse;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.exception.CalendarErrorCode;
import com.ssafy.fint.global.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final ActivityRepository activityRepository;

    public CalendarEventListResponse findEvents(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(CalendarErrorCode.INVALID_DATE_RANGE);
        }

        Long userId = SecurityUtils.currentUserId();
        Long tenantId = SecurityUtils.currentTenantId();

        OffsetDateTime startInclusive = startDate.atStartOfDay(KST).toOffsetDateTime();
        OffsetDateTime endExclusive = endDate.plusDays(1).atStartOfDay(KST).toOffsetDateTime();

        Page<Activity> page = activityRepository.searchByDateRange(
                userId, tenantId, startInclusive, endExclusive, pageable
        );
        log.debug("[CalendarEvents] userId={} tenantId={} startDate={} endDate={} total={}",
                userId, tenantId, startDate, endDate, page.getTotalElements());
        return CalendarEventListResponse.from(page);
    }

    public CalendarEventDetailResponse findEvent(String eventId) {
        Long activityId = parseActivityId(eventId);
        Long userId = SecurityUtils.currentUserId();
        Long tenantId = SecurityUtils.currentTenantId();

        Activity activity = activityRepository
                .findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(activityId, userId, tenantId)
                .orElseThrow(() -> new BusinessException(CalendarErrorCode.EVENT_NOT_FOUND));

        log.debug("[CalendarEventDetail] userId={} tenantId={} eventId={} activityId={}",
                userId, tenantId, eventId, activityId);
        return CalendarEventDetailResponse.from(activity);
    }

    private Long parseActivityId(String eventId) {
        if (!eventId.startsWith(CalendarEventConstants.EVENT_ID_PREFIX_FINT)) {
            throw new BusinessException(CalendarErrorCode.INVALID_EVENT_ID);
        }
        try {
            long id = Long.parseLong(eventId.substring(CalendarEventConstants.EVENT_ID_PREFIX_FINT.length()));
            if (id <= 0) {
                throw new BusinessException(CalendarErrorCode.INVALID_EVENT_ID);
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BusinessException(CalendarErrorCode.INVALID_EVENT_ID);
        }
    }
}

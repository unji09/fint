package com.ssafy.fint.domain.briefing;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.briefing.client.BriefingClient;
import com.ssafy.fint.domain.briefing.dto.BriefingRequest;
import com.ssafy.fint.domain.briefing.dto.BriefingResponse;
import com.ssafy.fint.domain.deal.entity.Deal;
import com.ssafy.fint.domain.signal.entity.AccountDartDisclosure;
import com.ssafy.fint.domain.signal.entity.AccountNewsArticle;
import com.ssafy.fint.domain.signal.repository.AccountDartDisclosureRepository;
import com.ssafy.fint.domain.signal.repository.AccountNewsArticleRepository;
import com.ssafy.fint.global.exception.ActivityErrorCode;
import com.ssafy.fint.global.exception.BusinessException;
import com.ssafy.fint.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class BriefingService {

    private static final int SIGNAL_DAYS_RANGE = 7;
    private static final DateTimeFormatter DART_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final ActivityRepository activityRepository;
    private final AccountNewsArticleRepository accountNewsArticleRepository;
    private final AccountDartDisclosureRepository accountDartDisclosureRepository;
    private final BriefingClient briefingClient;

    public BriefingResponse generateForActivity(Long activityId, CustomUserDetails me) {
        Activity activity = activityRepository
                .findByActivityIdAndUser_UserIdAndUser_Tenant_TenantId(
                        activityId, me.getUserId(), me.getTenantId())
                .orElseThrow(() -> new BusinessException(ActivityErrorCode.ACTIVITY_NOT_FOUND));

        if (activity.getType() != ActivityType.MEETING) {
            throw new BusinessException(ActivityErrorCode.MEETING_TYPE_REQUIRED);
        }
        if (activity.getDeal() == null) {
            log.info("[Briefing] activityId={} has no deal — cannot generate briefing", activityId);
            throw new BusinessException(ActivityErrorCode.DEAL_NOT_LINKED);
        }

        BriefingResponse response = doGenerate(activity, me.getTenantId());
        persistAndSave(activity, response);
        return response;
    }

    public void generateAndSave(Activity activity) {
        if (activity.getDeal() == null) {
            log.info("[Briefing] skip activityId={} — no deal associated", activity.getActivityId());
            return;
        }

        Long tenantId = activity.getUser().getTenant().getTenantId();

        BriefingResponse response;
        try {
            response = doGenerate(activity, tenantId);
        } catch (Exception e) {
            log.error("[Briefing] generation failed for activityId={}", activity.getActivityId(), e);
            return;
        }

        persistAndSave(activity, response);
    }

    private BriefingResponse doGenerate(Activity activity, Long tenantId) {
        Deal deal = activity.getDeal();
        Long accountId = deal.getAccount().getAccountId();
        BriefingRequest request = buildRequest(activity, deal, accountId, tenantId);

        log.info("[Briefing] requesting activityId={} accountId={} tenantId={}",
                activity.getActivityId(), accountId, tenantId);
        return briefingClient.generate(tenantId, request);
    }

    @Transactional
    public void persistAndSave(Activity activity, BriefingResponse response) {
        activity.updateBriefing(Map.of(
                "key_points", response.keyPoints(),
                "alerts", response.alerts()
        ));
        activityRepository.save(activity);
        log.info("[Briefing] saved activityId={} keyPoints={} alerts={}",
                activity.getActivityId(), response.keyPoints().size(), response.alerts().size());
    }

    private BriefingRequest buildRequest(Activity activity, Deal deal, Long accountId, Long tenantId) {
        String currentStage = deal.getCurrentPipeline() != null
                ? deal.getCurrentPipeline()
                : (activity.getPipelineStage() != null ? activity.getPipelineStage().getName() : null);
        return new BriefingRequest(
                activity.getActivityId(),
                activity.getTitle(),
                activity.getStartAt().toString(),
                accountId,
                deal.getAccount().getName(),
                deal.getAccount().getIndustry(),
                null,
                null,
                null,
                buildContacts(activity.getAttendees()),
                buildDeals(deal, currentStage),
                buildRecentMeetings(accountId, tenantId, activity.getStartAt()),
                buildSignals(accountId),
                null
        );
    }

    private List<BriefingRequest.ContactInfo> buildContacts(List<Map<String, Object>> attendees) {
        if (attendees == null || attendees.isEmpty()) {
            return List.of();
        }
        List<BriefingRequest.ContactInfo> result = new ArrayList<>();
        for (Map<String, Object> attendee : attendees) {
            Object nameObj = attendee.get("name");
            if (nameObj == null) {
                continue;
            }
            String position = attendee.containsKey("position") ? String.valueOf(attendee.get("position")) : null;
            String personality = attendee.containsKey("personality") ? String.valueOf(attendee.get("personality")) : null;
            result.add(new BriefingRequest.ContactInfo(nameObj.toString(), position, personality));
        }
        return result;
    }

    private List<BriefingRequest.DealSummary> buildDeals(Deal deal, String currentStage) {
        Integer probability = deal.getProbability() != null ? deal.getProbability().intValue() : null;
        Long amount = deal.getAmount() != null ? deal.getAmount().longValue() : null;
        return List.of(new BriefingRequest.DealSummary(
                deal.getDealId(),
                deal.getTitle(),
                currentStage,
                probability,
                amount
        ));
    }

    private List<BriefingRequest.MeetingHistory> buildRecentMeetings(Long accountId, Long tenantId, OffsetDateTime before) {
        return activityRepository.findRecentMeetingByAccountId(accountId, tenantId, before)
                .map(m -> List.of(new BriefingRequest.MeetingHistory(
                        m.getActivityId(),
                        m.getTitle(),
                        m.getStartAt().toLocalDate().toString(),
                        extractSummaryText(m),
                        null,
                        null
                )))
                .orElse(List.of());
    }

    private String extractSummaryText(Activity activity) {
        Map<String, Object> summary = activity.getSummary();
        if (summary == null) {
            return null;
        }
        Object text = summary.get("text");
        return text != null ? text.toString() : null;
    }

    private List<BriefingRequest.SignalItem> buildSignals(Long accountId) {
        OffsetDateTime since = OffsetDateTime.now().minusDays(SIGNAL_DAYS_RANGE);
        String dartSince = since.format(DART_DATE_FORMAT);
        List<BriefingRequest.SignalItem> result = new ArrayList<>();

        for (AccountNewsArticle ana : accountNewsArticleRepository.findRecentByAccountId(accountId, since)) {
            var article = ana.getNewsArticle();
            result.add(new BriefingRequest.SignalItem(
                    "NEWS",
                    article.getTitle(),
                    article.getContentSummary(),
                    article.getPublishedAt().toLocalDate().toString(),
                    null
            ));
        }

        for (AccountDartDisclosure ada : accountDartDisclosureRepository.findRecentByAccountId(accountId, dartSince)) {
            var disclosure = ada.getDartDisclosure();
            result.add(new BriefingRequest.SignalItem(
                    "DART",
                    disclosure.getReportNm(),
                    disclosure.getContentSummary(),
                    disclosure.getRceptDt(),
                    null
            ));
        }

        return result;
    }
}

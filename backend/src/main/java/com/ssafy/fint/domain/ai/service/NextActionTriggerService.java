package com.ssafy.fint.domain.ai.service;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import com.ssafy.fint.domain.ai.dto.NextActionCreateRequest;
import com.ssafy.fint.domain.ai.entity.TriggerType;
import com.ssafy.fint.domain.signal.entity.AccountDartDisclosure;
import com.ssafy.fint.domain.signal.entity.AccountNewsArticle;
import com.ssafy.fint.domain.signal.repository.AccountDartDisclosureRepository;
import com.ssafy.fint.domain.signal.repository.AccountNewsArticleRepository;
import com.ssafy.fint.domain.signal.service.SignalCollectService.SignalCollectResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NextActionTriggerService {

    private static final int SUPPLEMENT_NEWS_LIMIT = 3;
    private static final int SUPPLEMENT_DART_LIMIT = 1;
    private static final int SUPPLEMENT_DAYS = 7;
    private static final DateTimeFormatter DART_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final AiSuggestionService aiSuggestionService;
    private final ActivityRepository activityRepository;
    private final AccountNewsArticleRepository accountNewsArticleRepository;
    private final AccountDartDisclosureRepository accountDartDisclosureRepository;

    public void triggerFromCollectResult(Long tenantId, SignalCollectResult result) {
        Map<Long, AccountSignalChange> changes = groupByAccount(result);

        if (changes.isEmpty()) {
            log.debug("[NextActionTrigger] no account-level signal changes, skipping");
            return;
        }

        log.info("[NextActionTrigger] triggering AI for {} accounts", changes.size());

        for (Map.Entry<Long, AccountSignalChange> entry : changes.entrySet()) {
            Long accountId = entry.getKey();
            AccountSignalChange change = entry.getValue();

            try {
                TriggerType triggerType = determineTriggerType(change);
                List<Long> newsIds = mergeIds(change.newNewsIds, change.mappedNewsIds);
                List<Long> dartIds = mergeIds(change.newDartIds, change.mappedDartIds);
                List<Long> meetingIds = findRecentMeetingIds(accountId, tenantId);

                newsIds = supplementNewsIfNeeded(triggerType, newsIds, accountId);
                dartIds = supplementDartIfNeeded(triggerType, dartIds, accountId);

                NextActionCreateRequest request = new NextActionCreateRequest(
                        accountId, triggerType, newsIds, dartIds, meetingIds, null);

                aiSuggestionService.createNextActionBySystem(tenantId, request);

                log.info("[NextActionTrigger] accountId={} triggerType={} news={} dart={}",
                        accountId, triggerType, newsIds.size(), dartIds.size());
            } catch (Exception e) {
                log.error("[NextActionTrigger] failed for accountId={}", accountId, e);
            }
        }
    }

    static TriggerType determineTriggerType(AccountSignalChange change) {
        boolean hasNewNews = !change.newNewsIds.isEmpty();
        boolean hasNewDart = !change.newDartIds.isEmpty();
        boolean hasMappedNews = !change.mappedNewsIds.isEmpty();
        boolean hasMappedDart = !change.mappedDartIds.isEmpty();

        if (hasNewNews && hasNewDart) {
            return TriggerType.EXTERNAL_SIGNAL_UPDATED;
        }
        if (hasNewNews) {
            return TriggerType.NEWS_UPDATED;
        }
        if (hasNewDart) {
            return TriggerType.DART_UPDATED;
        }
        if (hasMappedNews && hasMappedDart) {
            return TriggerType.EXTERNAL_SIGNAL_UPDATED;
        }
        if (hasMappedNews) {
            return TriggerType.NEWS_MAPPED_TO_NEW_ACCOUNT;
        }
        if (hasMappedDart) {
            return TriggerType.DART_MAPPED_TO_NEW_ACCOUNT;
        }
        return TriggerType.NEWS_UPDATED;
    }

    private List<Long> findRecentMeetingIds(Long accountId, Long tenantId) {
        return activityRepository
                .findRecentMeetingsByAccountId(accountId, tenantId, OffsetDateTime.now())
                .stream()
                .map(Activity::getActivityId)
                .toList();
    }

    List<Long> supplementNewsIfNeeded(TriggerType triggerType, List<Long> newsIds, Long accountId) {
        if (!newsIds.isEmpty()) {
            return newsIds;
        }
        if (triggerType != TriggerType.DART_UPDATED && triggerType != TriggerType.DART_MAPPED_TO_NEW_ACCOUNT) {
            return newsIds;
        }
        OffsetDateTime since = OffsetDateTime.now().minusDays(SUPPLEMENT_DAYS);
        return accountNewsArticleRepository.findRecentByAccountId(accountId, since)
                .stream()
                .map(a -> a.getNewsArticle().getNewsArticleId())
                .limit(SUPPLEMENT_NEWS_LIMIT)
                .toList();
    }

    List<Long> supplementDartIfNeeded(TriggerType triggerType, List<Long> dartIds, Long accountId) {
        if (!dartIds.isEmpty()) {
            return dartIds;
        }
        if (triggerType != TriggerType.NEWS_UPDATED && triggerType != TriggerType.NEWS_MAPPED_TO_NEW_ACCOUNT) {
            return dartIds;
        }
        String since = LocalDate.now().minusDays(SUPPLEMENT_DAYS).format(DART_DATE_FORMAT);
        return accountDartDisclosureRepository.findRecentByAccountId(accountId, since)
                .stream()
                .map(a -> a.getDartDisclosure().getDartDisclosureId())
                .limit(SUPPLEMENT_DART_LIMIT)
                .toList();
    }

    private Map<Long, AccountSignalChange> groupByAccount(SignalCollectResult result) {
        Map<Long, AccountSignalChange> map = new HashMap<>();

        result.newNewsPerAccount().forEach((accountId, ids) ->
                map.computeIfAbsent(accountId, k -> new AccountSignalChange()).newNewsIds.addAll(ids));
        result.mappedNewsPerAccount().forEach((accountId, ids) ->
                map.computeIfAbsent(accountId, k -> new AccountSignalChange()).mappedNewsIds.addAll(ids));
        result.newDartPerAccount().forEach((accountId, ids) ->
                map.computeIfAbsent(accountId, k -> new AccountSignalChange()).newDartIds.addAll(ids));
        result.mappedDartPerAccount().forEach((accountId, ids) ->
                map.computeIfAbsent(accountId, k -> new AccountSignalChange()).mappedDartIds.addAll(ids));

        return map;
    }

    private List<Long> mergeIds(Set<Long> a, Set<Long> b) {
        Set<Long> merged = new HashSet<>(a);
        merged.addAll(b);
        return List.copyOf(merged);
    }

    static class AccountSignalChange {
        final Set<Long> newNewsIds = new HashSet<>();
        final Set<Long> mappedNewsIds = new HashSet<>();
        final Set<Long> newDartIds = new HashSet<>();
        final Set<Long> mappedDartIds = new HashSet<>();
    }
}

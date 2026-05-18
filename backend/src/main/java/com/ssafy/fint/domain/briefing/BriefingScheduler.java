package com.ssafy.fint.domain.briefing;

import com.ssafy.fint.domain.activity.entity.Activity;
import com.ssafy.fint.domain.activity.entity.ActivityType;
import com.ssafy.fint.domain.activity.repository.ActivityRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class BriefingScheduler {

    private static final int WINDOW_START_MINUTES = 29;
    private static final int WINDOW_END_MINUTES = 31;

    private final ActivityRepository activityRepository;
    private final BriefingService briefingService;

    @Scheduled(fixedRate = 60_000)
    public void triggerMeetingBriefings() {
        OffsetDateTime from = OffsetDateTime.now().plusMinutes(WINDOW_START_MINUTES);
        OffsetDateTime to = OffsetDateTime.now().plusMinutes(WINDOW_END_MINUTES);

        List<Activity> meetings =
                activityRepository.findUpcomingMeetingsWithoutBriefing(ActivityType.MEETING, from, to);

        if (meetings.isEmpty()) {
            return;
        }

        log.info("[BriefingScheduler] found {} meeting(s) to brief", meetings.size());
        meetings.forEach(briefingService::generateAndSave);
    }
}

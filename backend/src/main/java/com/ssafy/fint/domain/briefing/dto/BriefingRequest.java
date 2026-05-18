package com.ssafy.fint.domain.briefing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record BriefingRequest(
        @JsonProperty("activity_id") Long activityId,
        @JsonProperty("title") String title,
        @JsonProperty("scheduled_at") String scheduledAt,
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("account_name") String accountName,
        @JsonProperty("industry") String industry,
        @JsonProperty("current_mood") String currentMood,
        @JsonProperty("mood_score") Integer moodScore,
        @JsonProperty("mood_reason") String moodReason,
        @JsonProperty("contacts") List<ContactInfo> contacts,
        @JsonProperty("deals") List<DealSummary> deals,
        @JsonProperty("recent_meetings") List<MeetingHistory> recentMeetings,
        @JsonProperty("signals") List<SignalItem> signals,
        @JsonProperty("wiki_summary") String wikiSummary
) {

    public record ContactInfo(
            @JsonProperty("name") String name,
            @JsonProperty("position") String position,
            @JsonProperty("personality") String personality
    ) {}

    public record DealSummary(
            @JsonProperty("deal_id") Long dealId,
            @JsonProperty("title") String title,
            @JsonProperty("current_stage") String currentStage,
            @JsonProperty("probability") Integer probability,
            @JsonProperty("amount") Long amount
    ) {}

    public record MeetingHistory(
            @JsonProperty("activity_id") Long activityId,
            @JsonProperty("title") String title,
            @JsonProperty("date") String date,
            @JsonProperty("summary") String summary,
            @JsonProperty("mood_score") Integer moodScore,
            @JsonProperty("mood_reason") String moodReason
    ) {}

    public record SignalItem(
            @JsonProperty("signal_type") String signalType,
            @JsonProperty("title") String title,
            @JsonProperty("summary") String summary,
            @JsonProperty("published_at") String publishedAt,
            @JsonProperty("importance_score") Double importanceScore
    ) {}
}

package com.ssafy.fint.domain.dashboard.dto;

import com.ssafy.fint.domain.dashboard.entity.Dashboard;

import java.time.OffsetDateTime;

public record DashboardListResponse(
        Long dashboardId,
        String title,
        String thumbnailKey,
        OffsetDateTime lastAccessedAt
) {
    public static DashboardListResponse from(Dashboard dashboard) {
        return new DashboardListResponse(
                dashboard.getDashboardId(),
                dashboard.getTitle(),
                dashboard.getThumbnailKey(),
                dashboard.getLastAccessedAt()
        );
    }
}

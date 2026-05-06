package com.ssafy.fint.domain.calendar.controller;

import com.ssafy.fint.domain.calendar.dto.CalendarEventListResponse;
import com.ssafy.fint.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;

@Tag(name = "Calendar", description = "캘린더 일정 통합 조회 API")
public interface CalendarSwagger {

    @Operation(
            summary = "캘린더 일정 조회",
            description = "현재 사용자의 활동을 startDate~endDate 구간으로 조회한다. "
                    + "현재는 F!NT 직접 등록 활동만 반환하며 source=\"FINT\", eventId=\"act-{activityId}\" 형식이다. "
    )
    ApiResponse<CalendarEventListResponse> events(LocalDate startDate, LocalDate endDate, Pageable pageable);
}

package com.ssafy.fint.domain.calendar.controller;

import com.ssafy.fint.domain.calendar.dto.CalendarEventListResponse;
import com.ssafy.fint.domain.calendar.service.CalendarService;
import com.ssafy.fint.global.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/calendar")
@RequiredArgsConstructor
public class CalendarController implements CalendarSwagger {

    private final CalendarService calendarService;

    @Override
    @GetMapping("/events")
    public ApiResponse<CalendarEventListResponse> events(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return ApiResponse.ok(calendarService.findEvents(startDate, endDate, pageable));
    }
}

package com.ssafy.fint.domain.account.dto;

import com.ssafy.fint.domain.account.entity.AccountExternalInfo;
import com.ssafy.fint.domain.signal.entity.DartDisclosure;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public record AccountSignalResponse(
        String source,
        String title,
        String content,
        String url,
        OffsetDateTime occurredAt
) {

    private static final String DART_BASE_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";
    private static final ZoneOffset KST = ZoneOffset.of("+09:00");

    public static AccountSignalResponse from(AccountExternalInfo info) {
        return new AccountSignalResponse(
                info.getSource(),
                info.getTitle(),
                info.getContent(),
                info.getUrl(),
                info.getOccurredAt()
        );
    }

    public static AccountSignalResponse fromDart(DartDisclosure d) {
        return new AccountSignalResponse(
                "DART",
                d.getReportNm(),
                d.getContentSummary(),
                DART_BASE_URL + d.getRceptNo(),
                parseRceptDt(d.getRceptDt())
        );
    }

    private static OffsetDateTime parseRceptDt(String rceptDt) {
        return LocalDate.parse(rceptDt, DateTimeFormatter.BASIC_ISO_DATE)
                .atStartOfDay()
                .atOffset(KST);
    }
}

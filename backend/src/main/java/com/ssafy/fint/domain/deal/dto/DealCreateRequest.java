package com.ssafy.fint.domain.deal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record DealCreateRequest(

        @NotNull(message = "accountId 는 필수입니다.")
        Long accountId,

        Long teamId,

        @NotBlank(message = "title 은 필수입니다.")
        String title,

        LocalDate expectedClose,

        BigDecimal amount,

        @Valid
        List<ContactInput> contacts
) {

    /**
     * 딜에 연결할 담당자 입력.
     * contactId 가 있으면 기존 담당자 연결, 없으면 더미 담당자 생성 후 연결한다.
     * 신규 담당자 정식 등록은 담당자 등록 명세 확정 후 별도 PR 에서 도입 예정.
     */
    public record ContactInput(Long contactId) {
        public boolean isExisting() {
            return contactId != null;
        }
    }
}

package com.ssafy.fint.domain.contact.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 명함 OCR 추출 요청.
 * fileKey 는 S3 의 명함 이미지 키이며, FastAPI 명세에 따라 {@code business-cards/} prefix 를 강제한다.
 */
@Getter
@NoArgsConstructor
public class ContactOcrRequest {

    @NotBlank(message = "fileKey 는 필수입니다.")
    @Pattern(regexp = "^business-cards/.+", message = "fileKey 는 'business-cards/' 로 시작해야 합니다.")
    private String fileKey;
}

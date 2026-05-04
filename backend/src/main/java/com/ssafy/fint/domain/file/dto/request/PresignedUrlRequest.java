package com.ssafy.fint.domain.file.dto.request;

import com.ssafy.fint.domain.file.constant.FilePurpose;
import com.ssafy.fint.domain.file.constant.FileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 단일 업로드 presigned URL 발급 요청.
 * 명세: POST /api/v1/files/presigned-url
 *
 * S3 업로드 규칙(§3, §4)에 따른 purpose 별 ID:
 * - purpose=MEETING_RECORD → meetingId 필수
 * - purpose=OCR            → contactId 필수
 */
public record PresignedUrlRequest(
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull FileType fileType,
        @NotNull FilePurpose purpose,
        @Positive Long fileSize,
        @Positive Long meetingId,
        @Positive Long contactId
) {}

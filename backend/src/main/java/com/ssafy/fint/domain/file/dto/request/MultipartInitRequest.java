package com.ssafy.fint.domain.file.dto.request;

import com.ssafy.fint.domain.file.constant.FilePurpose;
import com.ssafy.fint.domain.file.constant.FileType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Multipart 업로드 init 요청.
 * 명세: POST /api/v1/files/multipart/init
 *
 * S3 업로드 규칙 §3: multipart 는 미팅 녹음(AUDIO + MEETING_RECORD) 전용 → meetingId 필수.
 */
public record MultipartInitRequest(
        @NotBlank(message = "fileName 은 비어 있을 수 없습니다.") String fileName,
        @NotBlank(message = "contentType 은 비어 있을 수 없습니다.") String contentType,
        @NotNull(message = "fileType 은 필수입니다. (IMAGE | AUDIO)") FileType fileType,
        @NotNull(message = "purpose 는 필수입니다. (MEETING_RECORD | OCR)") FilePurpose purpose,
        @NotNull(message = "fileSize 는 필수입니다.")
        @Positive(message = "fileSize 는 0보다 커야 합니다.") Long fileSize,
        @NotNull(message = "partCount 는 필수입니다.")
        @Min(value = 1, message = "partCount 는 1 이상이어야 합니다.")
        @Max(value = 10_000, message = "partCount 는 10000 이하여야 합니다.") Integer partCount,
        @NotNull(message = "meetingId 는 필수입니다.")
        @Positive(message = "meetingId 는 0보다 커야 합니다.") Long meetingId
) {}

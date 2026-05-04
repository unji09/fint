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
        @NotBlank String fileName,
        @NotBlank String contentType,
        @NotNull FileType fileType,
        @NotNull FilePurpose purpose,
        @NotNull @Positive Long fileSize,
        @NotNull @Min(1) @Max(10_000) Integer partCount,
        @NotNull @Positive Long meetingId
) {}

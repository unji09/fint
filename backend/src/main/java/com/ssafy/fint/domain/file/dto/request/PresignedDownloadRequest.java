package com.ssafy.fint.domain.file.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 다운로드 presigned URL 발급 요청.
 * 명세: POST /api/v1/files/presigned-download
 */
public record PresignedDownloadRequest(
        @NotBlank String fileKey,
        @Min(1) Integer expiresIn
) {}

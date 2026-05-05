package com.ssafy.fint.domain.file.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 다운로드 presigned URL 발급 요청.
 * 명세: POST /api/v1/files/presigned-download
 */
public record PresignedDownloadRequest(
        @NotBlank(message = "fileKey 는 비어 있을 수 없습니다.") String fileKey,
        @Min(value = 1, message = "expiresIn 은 1 이상이어야 합니다.") Integer expiresIn
) {}

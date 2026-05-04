package com.ssafy.fint.domain.file.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Multipart 업로드 완료 요청.
 * 명세: POST /api/v1/files/multipart/complete
 */
public record MultipartCompleteRequest(
        @NotBlank(message = "uploadId 는 비어 있을 수 없습니다.") String uploadId,
        @NotBlank(message = "fileKey 는 비어 있을 수 없습니다.") String fileKey,
        @NotEmpty(message = "parts 는 비어 있을 수 없습니다.")
        @Valid List<PartDto> parts
) {
    public record PartDto(
            @NotNull(message = "partNumber 는 필수입니다.")
            @Min(value = 1, message = "partNumber 는 1 이상이어야 합니다.") Integer partNumber,
            @NotBlank(message = "etag 는 비어 있을 수 없습니다.") String etag
    ) {}
}

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
        @NotBlank String uploadId,
        @NotBlank String fileKey,
        @NotEmpty @Valid List<PartDto> parts
) {
    public record PartDto(
            @NotNull @Min(1) Integer partNumber,
            @NotBlank String etag
    ) {}
}

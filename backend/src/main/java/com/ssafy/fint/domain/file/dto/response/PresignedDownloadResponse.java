package com.ssafy.fint.domain.file.dto.response;

public record PresignedDownloadResponse(
        String downloadUrl,
        long expiresIn
) {}

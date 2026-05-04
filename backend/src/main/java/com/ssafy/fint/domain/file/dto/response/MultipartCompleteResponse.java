package com.ssafy.fint.domain.file.dto.response;

public record MultipartCompleteResponse(
        String fileKey,
        long fileSize
) {}

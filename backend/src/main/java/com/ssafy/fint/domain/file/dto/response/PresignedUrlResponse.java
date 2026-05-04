package com.ssafy.fint.domain.file.dto.response;

import com.ssafy.fint.domain.file.constant.UploadType;

/**
 * 단일 업로드 presigned URL 응답.
 */
public record PresignedUrlResponse(
        String uploadUrl,
        String fileKey,
        long expiresIn,
        UploadType uploadType
) {
    public static PresignedUrlResponse single(String uploadUrl, String fileKey, long expiresIn) {
        return new PresignedUrlResponse(uploadUrl, fileKey, expiresIn, UploadType.SINGLE);
    }
}

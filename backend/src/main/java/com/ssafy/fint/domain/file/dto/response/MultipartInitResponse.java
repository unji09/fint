package com.ssafy.fint.domain.file.dto.response;

import com.ssafy.fint.domain.file.constant.UploadType;

import java.util.List;

public record MultipartInitResponse(
        String uploadId,
        String fileKey,
        List<PartUploadUrl> partUploadUrls,
        long expiresIn,
        UploadType uploadType
) {
    public record PartUploadUrl(int partNumber, String uploadUrl) {}

    public static MultipartInitResponse of(
            String uploadId, String fileKey, List<PartUploadUrl> urls, long expiresIn) {
        return new MultipartInitResponse(uploadId, fileKey, urls, expiresIn, UploadType.MULTIPART);
    }
}

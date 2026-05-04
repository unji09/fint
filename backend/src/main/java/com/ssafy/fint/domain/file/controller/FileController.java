package com.ssafy.fint.domain.file.controller;

import com.ssafy.fint.domain.file.dto.request.MultipartCompleteRequest;
import com.ssafy.fint.domain.file.dto.request.MultipartInitRequest;
import com.ssafy.fint.domain.file.dto.request.PresignedDownloadRequest;
import com.ssafy.fint.domain.file.dto.request.PresignedUrlRequest;
import com.ssafy.fint.domain.file.dto.response.MultipartCompleteResponse;
import com.ssafy.fint.domain.file.dto.response.MultipartInitResponse;
import com.ssafy.fint.domain.file.dto.response.PresignedDownloadResponse;
import com.ssafy.fint.domain.file.dto.response.PresignedUrlResponse;
import com.ssafy.fint.domain.file.service.FilePresignedService;
import com.ssafy.fint.global.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S3 presigned URL 기반 파일 업/다운로드 API.
 * BASE_URL = /api/v1/files
 */
@Tag(name = "File", description = "S3 presigned URL 파일 API")
@RestController
@RequestMapping("/files")
@RequiredArgsConstructor
public class FileController {

    private final FilePresignedService filePresignedService;

    @Operation(summary = "단일 업로드 presigned URL 발급",
            description = "이미지/소형 음성 파일을 S3에 직접 업로드하기 위한 PUT presigned URL 1개 발급 (≤100MB).")
    @PostMapping("/presigned-url")
    public ApiResponse<PresignedUrlResponse> issuePresignedUrl(
            @Valid @RequestBody PresignedUrlRequest request) {
        return ApiResponse.ok(filePresignedService.issueSingleUploadUrl(request));
    }

    @Operation(summary = "다운로드 presigned URL 발급",
            description = "S3 private 객체를 TTL 기반으로 다운로드하기 위한 GET presigned URL 발급 (default 300s).")
    @PostMapping("/presigned-download")
    public ApiResponse<PresignedDownloadResponse> issueDownloadUrl(
            @Valid @RequestBody PresignedDownloadRequest request) {
        return ApiResponse.ok(filePresignedService.issueDownloadUrl(request));
    }

    @Operation(summary = "Multipart 업로드 init",
            description = "대용량 음성 파일 분할 업로드용 presigned URL 목록 발급 (uploadId + partUploadUrls).")
    @PostMapping("/multipart/init")
    public ApiResponse<MultipartInitResponse> initMultipart(
            @Valid @RequestBody MultipartInitRequest request) {
        return ApiResponse.ok(filePresignedService.initMultipart(request));
    }

    @Operation(summary = "Multipart 업로드 complete",
            description = "분할 업로드된 파트를 ETag 기반으로 합쳐 S3에 최종 저장 완료 처리.")
    @PostMapping("/multipart/complete")
    public ApiResponse<MultipartCompleteResponse> completeMultipart(
            @Valid @RequestBody MultipartCompleteRequest request) {
        return ApiResponse.ok(filePresignedService.completeMultipart(request));
    }
}

package com.ssafy.fint.domain.file.dto.request;

import com.ssafy.fint.domain.file.constant.FilePurpose;
import com.ssafy.fint.domain.file.constant.FileType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 단일 업로드 presigned URL 발급 요청.
 * 명세: POST /api/v1/files/presigned-url
 *
 * S3 업로드 규칙(§3, §4)에 따른 purpose 별 ID:
 * - purpose=MEETING_RECORD → meetingId 필수
 * - purpose=OCR            → 추가 ID 불필요 (명함 등록 이전 단계라 contactId 가 아직 없음)
 */
public record PresignedUrlRequest(
        @NotBlank(message = "fileName 은 비어 있을 수 없습니다.") String fileName,
        @NotBlank(message = "contentType 은 비어 있을 수 없습니다.") String contentType,
        @NotNull(message = "fileType 은 필수입니다. (IMAGE | AUDIO)") FileType fileType,
        @NotNull(message = "purpose 는 필수입니다. (MEETING_RECORD | OCR)") FilePurpose purpose,
        @NotNull(message = "fileSize 는 필수입니다.")
        @Positive(message = "fileSize 는 0보다 커야 합니다.") Long fileSize,
        @Positive(message = "meetingId 는 0보다 커야 합니다.") Long meetingId
) {}

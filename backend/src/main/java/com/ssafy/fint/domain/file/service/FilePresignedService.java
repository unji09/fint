package com.ssafy.fint.domain.file.service;

import com.ssafy.fint.domain.file.constant.FilePurpose;
import com.ssafy.fint.domain.file.constant.FileType;
import com.ssafy.fint.domain.file.dto.request.MultipartCompleteRequest;
import com.ssafy.fint.domain.file.dto.request.MultipartInitRequest;
import com.ssafy.fint.domain.file.dto.request.PresignedDownloadRequest;
import com.ssafy.fint.domain.file.dto.request.PresignedUrlRequest;
import com.ssafy.fint.domain.file.dto.response.MultipartCompleteResponse;
import com.ssafy.fint.domain.file.dto.response.MultipartInitResponse;
import com.ssafy.fint.domain.file.dto.response.MultipartInitResponse.PartUploadUrl;
import com.ssafy.fint.domain.file.dto.response.PresignedDownloadResponse;
import com.ssafy.fint.domain.file.dto.response.PresignedUrlResponse;
import com.ssafy.fint.domain.file.exception.FileErrorCode;
import com.ssafy.fint.global.config.properties.AwsS3Properties;
import com.ssafy.fint.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadResponse;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ServerSideEncryption;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedUploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * S3 presigned URL 발급 + multipart 업로드 라이프사이클 처리.
 *
 * 본 구현은 `mydocs/file_api/S3 Presigned URL 업로드 규칙 ...md` 의 규칙을 강제한다:
 *  - §1 : SSE-KMS 필수 (alias/crm-fint-s3-key)
 *  - §2 : 발급 주체는 EC2 서버, S3 Key 는 서버에서만 생성
 *  - §3 : 미팅 녹음 → meetings/{meetingId}/{uuid}.{ext}, audio/* MIME
 *  - §4 : 명함 OCR  → business-cards/{uuid}.{ext}, image/* MIME, 5분/5MB
 *               (명함 등록 이전 단계라 contactId 없음)
 *  - §5 : PUT 만 발급, URL 1회용 (재사용 금지는 클라이언트 책임)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FilePresignedService {

    private static final String EXT_SAFE_CHARS = "[^A-Za-z0-9]";
    private static final DateTimeFormatter THUMBNAIL_TS = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final AwsS3Properties props;
    private final FileUploadValidator validator;

    // ---------------------------------------------------------------------
    // 1) 단일 업로드 presigned URL  (PUT, SSE-KMS)
    // ---------------------------------------------------------------------
    public PresignedUrlResponse issueSingleUploadUrl(PresignedUrlRequest req) {
        validator.validateTypePurpose(req.fileType(), req.purpose());
        validator.validateContentType(req.fileType(), req.contentType());
        validator.validateSize(req.purpose(), req.fileSize());

        String fileKey = buildFileKey(req.purpose(), req.fileName(), req.meetingId(), req.dashboardId());
        long expiresIn = switch (req.purpose()) {
            case OCR, THUMBNAIL -> props.presign().ocrUploadExpirationSeconds();
            case MEETING_RECORD -> props.presign().singleExpirationSeconds();
        };

        try {
            PutObjectRequest putReq = PutObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(fileKey)
                    .contentType(req.contentType())
                    // 규칙 §1, §2.2: SSE-KMS 필수
                    .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                    .ssekmsKeyId(props.kms().keyId())
                    .build();

            PutObjectPresignRequest presignReq = PutObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expiresIn))
                    .putObjectRequest(putReq)
                    .build();

            PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignReq);
            return PresignedUrlResponse.single(presigned.url().toString(), fileKey, expiresIn);
        } catch (AwsServiceException e) {
            log.error("[S3 presign PUT failed] key={}, err={}", fileKey, e.getMessage());
            throw new BusinessException(FileErrorCode.S3_PRESIGN_FAILED);
        }
    }

    // ---------------------------------------------------------------------
    // 2) 다운로드 presigned URL  (GET)
    // ---------------------------------------------------------------------
    public PresignedDownloadResponse issueDownloadUrl(PresignedDownloadRequest req) {
        // 클라이언트가 임의의 S3 Key 를 다운로드 URL 로 발급받지 못하도록 prefix 화이트리스트 검증
        validator.validateFileKey(req.fileKey());

        long expiresIn = (req.expiresIn() != null && req.expiresIn() > 0)
                ? req.expiresIn()
                : props.presign().downloadExpirationSeconds();

        try {
            GetObjectRequest getReq = GetObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(req.fileKey())
                    .build();

            GetObjectPresignRequest presignReq = GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(expiresIn))
                    .getObjectRequest(getReq)
                    .build();

            PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(presignReq);
            return new PresignedDownloadResponse(presigned.url().toString(), expiresIn);
        } catch (AwsServiceException e) {
            log.error("[S3 presign GET failed] key={}, err={}", req.fileKey(), e.getMessage());
            throw new BusinessException(FileErrorCode.S3_PRESIGN_FAILED);
        }
    }

    // ---------------------------------------------------------------------
    // 3) Multipart 업로드 init  (AUDIO + MEETING_RECORD 전용, SSE-KMS)
    // ---------------------------------------------------------------------
    public MultipartInitResponse initMultipart(MultipartInitRequest req) {
        // 규칙 §3: multipart 는 미팅 녹음 전용
        if (req.fileType() != FileType.AUDIO || req.purpose() != FilePurpose.MEETING_RECORD) {
            throw new BusinessException(FileErrorCode.MULTIPART_AUDIO_ONLY);
        }
        validator.validateContentType(req.fileType(), req.contentType());

        String fileKey = buildFileKey(FilePurpose.MEETING_RECORD, req.fileName(), req.meetingId(), null);
        long expiresIn = props.presign().multipartExpirationSeconds();

        CreateMultipartUploadResponse created;
        try {
            created = s3Client.createMultipartUpload(CreateMultipartUploadRequest.builder()
                    .bucket(props.bucket())
                    .key(fileKey)
                    .contentType(req.contentType())
                    // 규칙 §1: SSE-KMS 필수 (멀티파트는 init 단계에서 1회 지정)
                    .serverSideEncryption(ServerSideEncryption.AWS_KMS)
                    .ssekmsKeyId(props.kms().keyId())
                    .build());
        } catch (AwsServiceException e) {
            log.error("[S3 multipart init failed] key={}, err={}", fileKey, e.getMessage());
            throw new BusinessException(FileErrorCode.MULTIPART_INIT_FAILED);
        }

        String uploadId = created.uploadId();
        List<PartUploadUrl> partUrls = new ArrayList<>(req.partCount());
        try {
            for (int partNumber = 1; partNumber <= req.partCount(); partNumber++) {
                // 주의: UploadPart 자체에는 SSE 헤더를 포함하지 않는다.
                // multipart init 시 지정한 암호화 설정이 모든 파트에 자동 적용됨.
                UploadPartRequest partReq = UploadPartRequest.builder()
                        .bucket(props.bucket())
                        .key(fileKey)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build();

                UploadPartPresignRequest presignReq = UploadPartPresignRequest.builder()
                        .signatureDuration(Duration.ofSeconds(expiresIn))
                        .uploadPartRequest(partReq)
                        .build();

                PresignedUploadPartRequest presigned = s3Presigner.presignUploadPart(presignReq);
                partUrls.add(new PartUploadUrl(partNumber, presigned.url().toString()));
            }
        } catch (AwsServiceException e) {
            safeAbort(fileKey, uploadId);
            log.error("[S3 part presign failed] key={}, err={}", fileKey, e.getMessage());
            throw new BusinessException(FileErrorCode.S3_PRESIGN_FAILED);
        }

        return MultipartInitResponse.of(uploadId, fileKey, partUrls, expiresIn);
    }

    // ---------------------------------------------------------------------
    // 4) Multipart 업로드 complete
    // ---------------------------------------------------------------------
    public MultipartCompleteResponse completeMultipart(MultipartCompleteRequest req) {
        List<CompletedPart> completedParts = req.parts().stream()
                .sorted(Comparator.comparingInt(MultipartCompleteRequest.PartDto::partNumber))
                .map(p -> CompletedPart.builder()
                        .partNumber(p.partNumber())
                        .eTag(p.etag())
                        .build())
                .toList();

        try {
            s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(props.bucket())
                    .key(req.fileKey())
                    .uploadId(req.uploadId())
                    .multipartUpload(CompletedMultipartUpload.builder()
                            .parts(completedParts)
                            .build())
                    .build());
        } catch (AwsServiceException e) {
            log.error("[S3 multipart complete failed] key={}, uploadId={}, err={}",
                    req.fileKey(), req.uploadId(), e.getMessage());
            safeAbort(req.fileKey(), req.uploadId());
            throw new BusinessException(FileErrorCode.MULTIPART_COMPLETE_FAILED);
        }

        long size;
        try {
            HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(req.fileKey())
                    .build());
            size = head.contentLength() != null ? head.contentLength() : 0L;
        } catch (NoSuchKeyException e) {
            throw new BusinessException(FileErrorCode.FILE_NOT_FOUND);
        } catch (AwsServiceException e) {
            log.warn("[S3 headObject failed] key={}, err={}", req.fileKey(), e.getMessage());
            size = 0L;
        }

        return new MultipartCompleteResponse(req.fileKey(), size);
    }

    // ---------------------------------------------------------------------
    // 5) S3 객체 존재 확인
    // ---------------------------------------------------------------------
    public boolean existsOnS3(String fileKey) {
        try {
            s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(props.bucket())
                    .key(fileKey)
                    .build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (AwsServiceException e) {
            log.error("[S3 headObject failed] key={}, err={}", fileKey, e.getMessage());
            throw new BusinessException(FileErrorCode.S3_PRESIGN_FAILED);
        }
    }

    // ---------------------------------------------------------------------
    // helpers (S3 Key 생성 — 검증 책임은 FileUploadValidator 로 분리됨)
    // ---------------------------------------------------------------------

    /**
     * purpose 기반 S3 Key 생성 (서버에서만 결정 — 클라 위조 차단).
     *  - MEETING_RECORD → meetings/{meetingId}/{uuid}.{ext}
     *  - OCR            → business-cards/{uuid}.{ext}
     *  - THUMBNAIL      → thumbnails/{dashboardId}/{yyyyMMddHHmmssSSS}_{uuid}.{ext}
     */
    private String buildFileKey(FilePurpose purpose, String fileName,
                                Long meetingId, Long dashboardId) {
        String ext = extractExtension(fileName);
        String uuid = UUID.randomUUID().toString();

        return switch (purpose) {
            case MEETING_RECORD -> {
                if (meetingId == null) {
                    throw new BusinessException(FileErrorCode.MISSING_MEETING_ID);
                }
                yield props.upload().keyPrefix().meeting() + meetingId + "/" + uuid + ext;
            }
            case OCR -> props.upload().keyPrefix().businessCard() + uuid + ext;
            case THUMBNAIL -> {
                if (dashboardId == null) {
                    throw new BusinessException(FileErrorCode.MISSING_DASHBOARD_ID);
                }
                String ts = OffsetDateTime.now().format(THUMBNAIL_TS);
                yield props.upload().keyPrefix().thumbnail()
                        + dashboardId + "/" + ts + "_" + uuid + ext;
            }
        };
    }

    /** 파일명에서 확장자만 추출 (한글/공백/특수문자는 키에 포함하지 않음). */
    private String extractExtension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) return "";
        String raw = fileName.substring(dot + 1).replaceAll(EXT_SAFE_CHARS, "").toLowerCase();
        return raw.isEmpty() ? "" : "." + raw;
    }

    private void safeAbort(String fileKey, String uploadId) {
        try {
            s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                    .bucket(props.bucket())
                    .key(fileKey)
                    .uploadId(uploadId)
                    .build());
        } catch (Exception ignore) {
            log.warn("[S3 abortMultipartUpload failed] key={}, uploadId={}", fileKey, uploadId);
        }
    }
}

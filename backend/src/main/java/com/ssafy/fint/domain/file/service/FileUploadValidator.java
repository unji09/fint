package com.ssafy.fint.domain.file.service;

import com.ssafy.fint.domain.file.constant.FilePurpose;
import com.ssafy.fint.domain.file.constant.FileType;
import com.ssafy.fint.domain.file.exception.FileErrorCode;
import com.ssafy.fint.global.config.properties.AwsS3Properties;
import com.ssafy.fint.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 파일 업/다운로드 요청에 대한 검증 책임만 담당.
 *
 * {@link FilePresignedService} 에서 분리된 helper 로,
 * S3 호출 등 인프라 책임은 갖지 않는다.
 *
 * 모든 메서드는 위반 시 {@link BusinessException} 을 던진다.
 */
@Component
@RequiredArgsConstructor
public class FileUploadValidator {

    /** fileKey 최대 길이 (S3 한계 1024). 여유 있게 제한. */
    private static final int MAX_FILE_KEY_LENGTH = 1024;

    private final AwsS3Properties props;

    /** 규칙 §3, §4: fileType ↔ purpose 조합 검증. */
    public void validateTypePurpose(FileType type, FilePurpose purpose) {
        boolean ok = switch (purpose) {
            case MEETING_RECORD -> type == FileType.AUDIO;
            case OCR, THUMBNAIL -> type == FileType.IMAGE;
        };
        if (!ok) {
            throw new BusinessException(FileErrorCode.INVALID_PURPOSE_FOR_TYPE,
                    "fileType=" + type + ", purpose=" + purpose);
        }
    }

    /** Content-Type 이 fileType 의 MIME 그룹과 일치하는지 검증. */
    public void validateContentType(FileType type, String contentType) {
        String lc = contentType.toLowerCase();
        boolean ok = switch (type) {
            case IMAGE -> lc.startsWith("image/");
            case AUDIO -> lc.startsWith("audio/");
        };
        if (!ok) {
            throw new BusinessException(FileErrorCode.INVALID_CONTENT_TYPE,
                    "fileType=" + type + ", contentType=" + contentType);
        }
    }

    /**
     * purpose 별 단일 업로드 최대 크기 검증.
     *
     * 호출 시점의 fileSize 는 DTO 에서 {@code @NotNull @Positive} 로 보장되므로
     * 별도의 null 검증은 수행하지 않는다.
     */
    public void validateSize(FilePurpose purpose, long fileSize) {
        long max = switch (purpose) {
            case OCR -> props.upload().maxOcrSize();
            case THUMBNAIL -> props.upload().maxThumbnailSize();
            case MEETING_RECORD -> props.upload().maxSingleSize();
        };
        if (fileSize > max) {
            throw new BusinessException(FileErrorCode.FILE_TOO_LARGE,
                    "fileSize=" + fileSize + " > max=" + max + " (purpose=" + purpose + ")");
        }
    }

    /**
     * 다운로드 요청에서 들어온 fileKey 가 서버가 발급할 수 있는 형태인지 검증.
     *
     * - 허용 prefix: {@code upload.key-prefix.meeting} / {@code upload.key-prefix.business-card}
     * - 경로 탈출 / 절대경로 / 백슬래시 / 제어문자 차단
     *
     * 클라이언트가 임의의 S3 Key 를 다운로드 URL 로 발급받지 못하도록
     * 화이트리스트 prefix 기반으로 제한한다.
     */
    public void validateFileKey(String fileKey) {
        if (fileKey == null || fileKey.isBlank()) {
            throw new BusinessException(FileErrorCode.INVALID_FILE_KEY, "fileKey 가 비어있습니다.");
        }
        if (fileKey.length() > MAX_FILE_KEY_LENGTH) {
            throw new BusinessException(FileErrorCode.INVALID_FILE_KEY,
                    "fileKey 길이 초과: " + fileKey.length());
        }
        if (fileKey.startsWith("/")
                || fileKey.contains("..")
                || fileKey.contains("\\")
                || containsControlChar(fileKey)) {
            throw new BusinessException(FileErrorCode.INVALID_FILE_KEY,
                    "fileKey 에 허용되지 않는 문자가 포함되어 있습니다.");
        }

        String meetingPrefix = props.upload().keyPrefix().meeting();
        String cardPrefix = props.upload().keyPrefix().businessCard();
        String thumbnailPrefix = props.upload().keyPrefix().thumbnail();
        if (!fileKey.startsWith(meetingPrefix)
                && !fileKey.startsWith(cardPrefix)
                && !fileKey.startsWith(thumbnailPrefix)) {
            throw new BusinessException(FileErrorCode.INVALID_FILE_KEY,
                    "허용되지 않은 fileKey prefix 입니다.");
        }
    }

    private static boolean containsControlChar(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isISOControl(s.charAt(i))) return true;
        }
        return false;
    }
}

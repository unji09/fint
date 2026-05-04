package com.ssafy.fint.domain.file.exception;

import com.ssafy.fint.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 파일/S3 도메인 에러 코드. prefix: FL
 */
@Getter
@RequiredArgsConstructor
public enum FileErrorCode implements ErrorCode {

    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "FL001", "지원하지 않는 파일 타입입니다."),
    INVALID_CONTENT_TYPE(HttpStatus.BAD_REQUEST, "FL002", "Content-Type 이 파일 타입과 일치하지 않습니다."),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST, "FL003", "단일 업로드 허용 크기를 초과했습니다. multipart 업로드를 사용하세요."),
    INVALID_PART_COUNT(HttpStatus.BAD_REQUEST, "FL004", "유효하지 않은 파트 개수입니다. (1 ~ 10000)"),
    EMPTY_PARTS(HttpStatus.BAD_REQUEST, "FL005", "완료할 파트 정보가 비어있습니다."),
    MULTIPART_AUDIO_ONLY(HttpStatus.BAD_REQUEST, "FL006", "Multipart 업로드는 AUDIO 타입만 지원합니다."),
    MISSING_MEETING_ID(HttpStatus.BAD_REQUEST, "FL007", "MEETING_RECORD 업로드에는 meetingId 가 필요합니다."),
    MISSING_CONTACT_ID(HttpStatus.BAD_REQUEST, "FL008", "OCR 업로드에는 contactId 가 필요합니다."),
    INVALID_PURPOSE_FOR_TYPE(HttpStatus.BAD_REQUEST, "FL009", "fileType 과 purpose 조합이 올바르지 않습니다."),

    S3_PRESIGN_FAILED(HttpStatus.BAD_GATEWAY, "FL501", "Presigned URL 발급에 실패했습니다."),
    MULTIPART_INIT_FAILED(HttpStatus.BAD_GATEWAY, "FL502", "Multipart 업로드 초기화에 실패했습니다."),
    MULTIPART_COMPLETE_FAILED(HttpStatus.BAD_GATEWAY, "FL503", "Multipart 업로드 완료 처리에 실패했습니다."),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND, "FL301", "S3 객체를 찾을 수 없습니다."),
    ;

    private final HttpStatus status;
    private final String code;
    private final String message;
}

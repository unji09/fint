package com.ssafy.fint.global.exception;

import com.ssafy.fint.global.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.HttpClientErrorException;

import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 도메인 예외 단일 창구.
     * 상태 코드가 런타임에 결정되므로 {@link ResponseEntity} 사용.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        log.warn("[{}] {}", errorCode.getCode(), ex.getMessage());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.fail(errorCode, ex.getMessage()));
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResponse<Map<String, String>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (msg1, msg2) -> msg1
                ));
        log.warn("[ValidationError] {}", fieldErrors);
        return ApiResponse.fail(CommonErrorCode.INVALID_INPUT, CommonErrorCode.INVALID_INPUT.getMessage(), fieldErrors);
    }

    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    @ExceptionHandler(HttpClientErrorException.class)
    public ApiResponse<Void> handleHttpClientError(HttpClientErrorException ex) {
        // 외부 API raw body 로그 금지 — 상태 코드만 기록
        log.error("[ExternalApiError] status={}", ex.getStatusCode());
        return ApiResponse.fail(CommonErrorCode.EXTERNAL_API_FAILED);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiResponse<Void> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("[IllegalArgument] {}", ex.getMessage());
        return ApiResponse.fail(CommonErrorCode.ILLEGAL_ARGUMENT, ex.getMessage());
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    @ExceptionHandler(RuntimeException.class)
    public ApiResponse<Void> handleRuntime(RuntimeException ex) {
        log.error("[InternalError] {}", ex.getMessage(), ex);
        return ApiResponse.fail(CommonErrorCode.INTERNAL_SERVER_ERROR);
    }
}

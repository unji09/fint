package com.ssafy.fint.global;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.ssafy.fint.global.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@JsonPropertyOrder({"status", "code", "message", "data"})
public class ApiResponse<T> {

    @Schema(example = "200")
    private final int status;

    /**
     * 실패 응답에만 채워지는 도메인 에러 코드 (예: "U001", "AU002").
     * 성공 응답에서는 null 이며 {@code default-property-inclusion: non_null} 설정에 의해 JSON 에서 생략된다.
     */
    @Schema(example = "U001")
    private final String code;

    @Schema(example = "success")
    private final String message;

    private final T data;

    private ApiResponse(int status, String code, String message, T data) {
        this.status = status;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    // ---------- 성공 ----------

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), null, "success", data);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(HttpStatus.OK.value(), null, "success", null);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(HttpStatus.CREATED.value(), null, "success", data);
    }

    // ---------- 실패 (ErrorCode 기반, 권장) ----------

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) {
        return new ApiResponse<>(
                errorCode.getStatus().value(),
                errorCode.getCode(),
                errorCode.getMessage(),
                null
        );
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(
                errorCode.getStatus().value(),
                errorCode.getCode(),
                message,
                null
        );
    }

    public static <T> ApiResponse<T> fail(ErrorCode errorCode, String message, T data) {
        return new ApiResponse<>(
                errorCode.getStatus().value(),
                errorCode.getCode(),
                message,
                data
        );
    }

    // ---------- 실패 (프레임워크 예외 등, code 없음) ----------

    public static <T> ApiResponse<T> fail(HttpStatus status, String message) {
        return new ApiResponse<>(status.value(), null, message, null);
    }

    public static <T> ApiResponse<T> fail(HttpStatus status, String message, T data) {
        return new ApiResponse<>(status.value(), null, message, data);
    }
}

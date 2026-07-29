package com.vccorp.eap.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * Lớp bọc phản hồi API chuẩn hóa theo thiết kế chi tiết (DetailedDesign §3.2).
 * Hỗ trợ định dạng lồng nhau cho đối tượng error.
 */
public record ApiResponse<T>(
    boolean success,
    T data,
    ErrorDetail error
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(
        String errorCode,
        String message,
        Map<String, String> errors
    ) {}

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> error(String errorCode, String message) {
        return new ApiResponse<>(false, null, new ErrorDetail(errorCode, message, null));
    }

    public static ApiResponse<Void> error(String errorCode, String message, Map<String, String> errors) {
        return new ApiResponse<>(false, null, new ErrorDetail(errorCode, message, errors));
    }
}

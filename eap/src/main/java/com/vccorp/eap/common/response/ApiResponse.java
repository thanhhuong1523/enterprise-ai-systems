package com.vccorp.eap.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String code;
    private String timestamp;
    private T data;
    private String message;
    private Map<String, String> errors;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .code("SUCCESS")
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .data(data)
                .build();
    }

    public static ApiResponse<Void> error(String code, String message) {
        return ApiResponse.<Void>builder()
                .success(false)
                .code(code)
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .message(message)
                .build();
    }

    public static ApiResponse<Void> error(String code, String message, Map<String, String> errors) {
        return ApiResponse.<Void>builder()
                .success(false)
                .code(code)
                .timestamp(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .message(message)
                .errors(errors)
                .build();
    }
}

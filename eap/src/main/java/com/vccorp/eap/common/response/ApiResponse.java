package com.vccorp.eap.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    boolean success,
    String code,
    String timestamp,
    T data,
    String message,
    Map<String, String> errors
) {
    public static <T> Builder<T> builder(boolean success, String code, String timestamp) {
        return new Builder<>(success, code, timestamp);
    }

    public static class Builder<T> {
        private final boolean success;
        private final String code;
        private final String timestamp;
        private T data;
        private String message;
        private Map<String, String> errors;

        public Builder(boolean success, String code, String timestamp) {
            this.success = success;
            this.code = code;
            this.timestamp = timestamp;
        }

        public Builder<T> data(T data) {
            this.data = data;
            return this;
        }

        public Builder<T> message(String message) {
            this.message = message;
            return this;
        }

        public Builder<T> errors(Map<String, String> errors) {
            this.errors = errors;
            return this;
        }

        public ApiResponse<T> build() {
            return new ApiResponse<>(success, code, timestamp, data, message, errors);
        }
    }

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder(true, "SUCCESS", DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .data(data)
                .build();
    }

    public static ApiResponse<Void> error(String code, String message) {
        return ApiResponse.<Void>builder(false, code, DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .message(message)
                .build();
    }

    public static ApiResponse<Void> error(String code, String message, Map<String, String> errors) {
        return ApiResponse.<Void>builder(false, code, DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
                .message(message)
                .errors(errors)
                .build();
    }
}

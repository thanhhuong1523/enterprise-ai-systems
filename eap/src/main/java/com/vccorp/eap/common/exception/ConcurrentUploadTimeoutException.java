package com.vccorp.eap.common.exception;

/**
 * Thrown by DocumentServiceImpl when pg_try_advisory_xact_lock cannot be acquired
 * after the maximum retry budget (5 retries, ~2 seconds of exponential backoff).
 * <p>
 * Mapped to HTTP 429 Too Many Requests by GlobalExceptionHandler.
 * Suppresses stack-trace generation for performance under high-concurrency load.
 */
public class ConcurrentUploadTimeoutException extends RuntimeException {

    public ConcurrentUploadTimeoutException() {
        super("Yêu cầu tải lên tệp tin đang được xử lý đồng thời. Vui lòng thử lại sau.",
                null, true, false);
    }

    public ConcurrentUploadTimeoutException(String message) {
        super(message, null, true, false);
    }
}

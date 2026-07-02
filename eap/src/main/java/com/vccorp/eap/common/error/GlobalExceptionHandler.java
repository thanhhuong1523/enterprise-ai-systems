package com.vccorp.eap.common.error;

import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        ApiResponse<Void> response = ApiResponse.error(errorCode.name(), ex.getMessage());
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        ErrorCode errorCode = ErrorCode.ERR_FORBIDDEN_ROLE;
        ApiResponse<Void> response = ApiResponse.error(errorCode.name(), errorCode.getDefaultMessage());
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        ApiResponse<Void> response = ApiResponse.error(errorCode.name(), "Dữ liệu không hợp lệ.");
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {
        ex.printStackTrace(); // In ra stacktrace để gỡ lỗi nhanh
        ErrorCode errorCode = ErrorCode.ERR_SYSTEM_ERROR;
        ApiResponse<Void> response = ApiResponse.error(errorCode.name(), errorCode.getDefaultMessage());
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }
}

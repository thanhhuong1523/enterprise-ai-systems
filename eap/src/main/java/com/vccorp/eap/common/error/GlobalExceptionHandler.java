package com.vccorp.eap.common.error;

import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Map;

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
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(java.util.stream.Collectors.toMap(
                        org.springframework.validation.FieldError::getField,
                        error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Giá trị không hợp lệ",
                        (existing, replacement) -> existing
                ));
        ApiResponse<Void> response = ApiResponse.error(errorCode.name(), "Dữ liệu không hợp lệ.", errors);
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatchException(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        String errorMessage = String.format("Trường '%s' không đúng định dạng. Yêu cầu kiểu dữ liệu '%s'.", 
                ex.getName(), ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "hợp lệ");
        ApiResponse<Void> response = ApiResponse.error(errorCode.name(), errorMessage);
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }

    @ExceptionHandler(org.springframework.web.bind.MissingPathVariableException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingPathVariableException(org.springframework.web.bind.MissingPathVariableException ex) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        String errorMessage = String.format("Thiếu tham số bắt buộc trên đường dẫn: '%s'.", ex.getVariableName());
        ApiResponse<Void> response = ApiResponse.error(errorCode.name(), errorMessage);
        return new ResponseEntity<>(response, errorCode.getHttpStatus());
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(jakarta.validation.ConstraintViolationException ex) {
        ErrorCode errorCode = ErrorCode.VALIDATION_ERROR;
        Map<String, String> errors = ex.getConstraintViolations().stream()
                .collect(java.util.stream.Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage(),
                        (existing, replacement) -> existing
                ));
        ApiResponse<Void> response = ApiResponse.error(errorCode.name(), "Dữ liệu không hợp lệ.", errors);
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

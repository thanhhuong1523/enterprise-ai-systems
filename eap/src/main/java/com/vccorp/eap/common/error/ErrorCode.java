package com.vccorp.eap.common.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    ERR_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập hết hạn hoặc không hợp lệ."),
    ERR_OWNERSHIP_VIOLATION(HttpStatus.NOT_FOUND, "Tài liệu yêu cầu không tồn tại."),
    ERR_FORBIDDEN_ROLE(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện hành động này."),
    ERR_BOARD_PROTECTION(HttpStatus.BAD_REQUEST, "Cấm tạo liên kết Alias đối với tài liệu của phòng BOARD."),
    ERR_DUPLICATE_ALIAS(HttpStatus.BAD_REQUEST, "Phòng ban nhận đã nhận một liên kết đang hoạt động từ tài liệu này."),
    ERR_DOCUMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Tài liệu yêu cầu không tồn tại."),
    ERR_CONCURRENT_UPLOAD(HttpStatus.TOO_MANY_REQUESTS, "Yêu cầu tải lên tệp tin đang được xử lý đồng thời. Vui lòng thử lại sau."),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ."),
    ERR_SYSTEM_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống."),
    ERR_DUPLICATE_DOCUMENT(HttpStatus.CONFLICT, "Tài liệu đã tồn tại trong phòng ban.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}

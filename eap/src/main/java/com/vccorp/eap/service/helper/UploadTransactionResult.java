package com.vccorp.eap.service.helper;

import com.vccorp.eap.dto.DocumentResponse;

/**
 * Kết quả của việc thực thi giao dịch tải lên tài liệu (§4.2, ADR-008).
 * <p>
 * Biểu diễn trạng thái giao dịch: thành công (kèm metadata) hoặc khóa bận (LOCK_BUSY).
 */
public record UploadTransactionResult(
    Status status,
    DocumentResponse response
) {
    public enum Status {
        SUCCESS,
        LOCK_BUSY
    }

    public static UploadTransactionResult success(DocumentResponse response) {
        return new UploadTransactionResult(Status.SUCCESS, response);
    }

    public static UploadTransactionResult lockBusy() {
        return new UploadTransactionResult(Status.LOCK_BUSY, null);
    }
}

package com.vccorp.eap.worker;

import java.util.UUID;

/**
 * Dịch vụ ghi nhận điểm kiểm tra (checkpoint) tiến trình xử lý tài liệu của Worker.
 */
public interface CheckpointService {
    
    /**
     * Lưu lại điểm kiểm tra (checkpoint) sau khi hoàn thành một phân đoạn của tài liệu.
     * Xác thực quyền sở hữu của Worker trước khi cập nhật.
     * @throws com.vccorp.eap.common.exception.BusinessException (ErrorCode.ERR_OWNERSHIP_LOST) nếu Worker bị cướp quyền sở hữu.
     */
    void commitCheckpoint(UUID id, String workerId, int chunkIndex);
}

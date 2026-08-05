package com.vccorp.eap.service.helper;

import java.util.UUID;

/**
 * Interface Facade kết hợp các nghiệp vụ kiểm tra trùng lặp và đồng bộ hóa tài liệu.
 * Đóng vai trò giảm khớp nối (coupling) cho core service bằng cách che giấu các chi tiết khóa Advisory Lock và truy vấn trùng lặp.
 */
public interface DocumentDeduplicationManager {
    
    /**
     * Kiểm tra nhanh trùng lặp (không khóa) trước khi bắt đầu transaction dài hơi.
     * @throws com.vccorp.eap.common.exception.BusinessException nếu tài liệu đã tồn tại trong phòng ban.
     */
    void fastCheckDuplicate(String hash, UUID departmentId);
    
    /**
     * Cố gắng giành Advisory Lock cho cặp phòng ban và mã băm hash.
     */
    boolean tryAcquireLock(UUID departmentId, String hash);
    
    /**
     * Thực hiện kiểm tra trùng lặp gộp trong khi đã giữ Advisory Lock.
     * Đảm bảo tính nhất quán (Double-Checked Locking pattern).
     */
    DeduplicationQueryResult doubleCheckDuplicate(String hash, UUID departmentId);
}

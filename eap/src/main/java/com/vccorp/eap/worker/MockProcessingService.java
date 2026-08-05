package com.vccorp.eap.worker;

import java.util.UUID;

/**
 * Dịch vụ giả lập tác vụ xử lý tính toán trên từng phân đoạn (chunk) của tài liệu.
 */
public interface MockProcessingService {
    
    /**
     * Giả lập việc xử lý một phân đoạn cụ thể của tài liệu.
     * Thao tác này thường sleep một khoảng thời gian cấu hình được để mô phỏng tác vụ nặng.
     */
    void processChunk(UUID documentId, int chunkIndex) throws InterruptedException;
}

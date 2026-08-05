package com.vccorp.eap.worker;

import java.util.UUID;

/**
 * Thành phần chịu trách nhiệm thực thi trực tiếp tiến trình phân mảnh và xử lý tài liệu.
 */
public interface WorkerExecutor {
    
    /**
     * Thực thi tác vụ xử lý phân đoạn cho tài liệu.
     * Kiểm tra tính toàn vẹn của tệp tin vật lý, chia nhỏ tệp thành các phân đoạn,
     * thực hiện tính toán giả lập và định kỳ ghi checkpoint lên DB.
     */
    void executeTask(UUID taskId, String workerId);
}

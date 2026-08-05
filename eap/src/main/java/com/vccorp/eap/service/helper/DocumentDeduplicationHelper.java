package com.vccorp.eap.service.helper;

import java.util.UUID;

/**
 * Interface chịu trách nhiệm thực thi các câu truy vấn cơ sở dữ liệu chuyên biệt để kiểm tra trùng lặp tệp.
 */
public interface DocumentDeduplicationHelper {
    
    /**
     * Thực hiện kiểm tra gộp (Aggregate Check) trên bảng tài liệu dựa trên hash và phòng ban.
     * Trả về kết quả cho biết phòng ban đã có bản ghi trùng chưa, id của bản ghi đó là gì,
     * và tệp tin vật lý cũ nhất tương ứng với mã băm này nằm ở đâu (phục vụ Single Instance Storage).
     */
    DeduplicationQueryResult executeAggregateCheck(String hash, UUID departmentId);
}

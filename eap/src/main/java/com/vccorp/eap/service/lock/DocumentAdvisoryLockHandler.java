package com.vccorp.eap.service.lock;

import java.util.UUID;

/**
 * Interface chịu trách nhiệm xử lý khóa đồng thời cấp độ cơ sở dữ liệu.
 * Sử dụng cơ chế PostgreSQL Advisory Lock để đảm bảo tính đồng bộ khi xử lý các file cùng hash.
 */
public interface DocumentAdvisoryLockHandler {
    
    /**
     * Cố gắng lấy Advisory Lock tương ứng với cặp giá trị phòng ban và mã băm của tệp tin.
     * Khóa này tự giải phóng khi transaction chứa nó kết thúc (commit hoặc rollback).
     * @return true nếu lấy khóa thành công, false nếu khóa đang bị giữ bởi tiến trình khác.
     */
    boolean tryAcquireLock(UUID departmentId, String hash);
}

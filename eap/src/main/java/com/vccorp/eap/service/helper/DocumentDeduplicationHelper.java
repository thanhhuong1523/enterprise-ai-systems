package com.vccorp.eap.service.helper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Thực thi truy vấn gộp Aggregate (Query 2) dùng cho Fast-Check và Double-Check (§1.3, ADR-012).
 * <p>
 * Truy vấn này giảm số DB round-trips từ 2 xuống 1 bằng cách trả về cùng lúc:
 * - has_active_in_dept: tài liệu hoạt động trong phòng ban
 * - active_doc_id:      UUID của bản ghi hoạt động đó
 * - oldest_file_ref:    đường dẫn tệp vật lý cũ nhất trên toàn hệ thống (SIS lookup)
 */
@Component
public class DocumentDeduplicationHelper {

    private static final Logger log = LoggerFactory.getLogger(DocumentDeduplicationHelper.class);

    /**
     * Query 2 (§5): truy vấn Aggregate quét theo idx_documents_hash.
     * {@code stringtype=unspecified} trong JDBC URL cho phép PostgreSQL tự suy kiểu UUID từ cột.
     */
    private static final String SQL_AGGREGATE_CHECK =
            "SELECT " +
            "    bool_or(owner_department_id = ? AND deleted_at IS NULL) AS has_active_in_dept, " +
            "    (array_agg(id ORDER BY created_at ASC) FILTER (WHERE owner_department_id = ? AND deleted_at IS NULL))[1] AS active_doc_id, " +
            "    (array_agg(file_reference ORDER BY created_at ASC) FILTER (WHERE file_reference IS NOT NULL))[1] AS oldest_file_ref " +
            "FROM documents " +
            "WHERE hash = ?";

    /**
     * Thực thi truy vấn gộp Aggregate (Query 2) trên kết nối JDBC hiện tại.
     * <p>
     * Khi gọi trong {@code TransactionTemplate.execute()}, {@code jdbcTemplate} sử dụng
     * đúng kết nối vật lý đang được ghim (Pinned Connection) — đảm bảo Advisory Lock
     * và Double-Check cùng chạy trên một connection (ADR-008).
     *
     * @param jdbcTemplate JdbcTemplate được inject vào DocumentServiceImpl
     * @param hash         SHA-256 hex hash của tệp tin
     * @param departmentId ID phòng ban của người dùng hiện tại
     * @return kết quả Aggregate gồm 3 trường logic
     */
    public DeduplicationQueryResult executeAggregateCheck(JdbcTemplate jdbcTemplate, String hash, UUID departmentId) {
        String deptIdStr = departmentId.toString();
        log.debug("Executing aggregate check: hash={}, dept={}", hash, deptIdStr);

        return jdbcTemplate.queryForObject(SQL_AGGREGATE_CHECK, (rs, rowNum) -> {
            boolean hasActiveInDept = rs.getBoolean("has_active_in_dept");
            String activeDocIdStr = rs.getString("active_doc_id");
            UUID activeDocId = (activeDocIdStr != null) ? UUID.fromString(activeDocIdStr) : null;
            String oldestFileRef = rs.getString("oldest_file_ref");
            return new DeduplicationQueryResult(hasActiveInDept, activeDocId, oldestFileRef);
        }, deptIdStr, deptIdStr, hash);
    }
}

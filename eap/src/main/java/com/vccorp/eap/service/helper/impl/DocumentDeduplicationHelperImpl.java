package com.vccorp.eap.service.helper.impl;

import com.vccorp.eap.service.helper.DeduplicationQueryResult;
import com.vccorp.eap.service.helper.DocumentDeduplicationHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Lớp thực thi của DocumentDeduplicationHelper.
 * Thực thi câu lệnh SQL native tối ưu hóa gộp (bool_or, array_agg) để trả về thông tin trùng lặp nhanh nhất.
 */
@Component
public class DocumentDeduplicationHelperImpl implements DocumentDeduplicationHelper {

    private static final Logger log = LoggerFactory.getLogger(DocumentDeduplicationHelperImpl.class);

    /** Query SQL kiểm tra trùng lặp gộp chuyên sâu trên PostgreSQL */
    private static final String SQL_AGGREGATE_CHECK =
            "SELECT " +
            "    bool_or(owner_department_id = ? AND deleted_at IS NULL) AS has_active_in_dept, " +
            "    (array_agg(id ORDER BY created_at ASC) FILTER (WHERE owner_department_id = ? AND deleted_at IS NULL))[1] AS active_doc_id, " +
            "    (array_agg(file_reference ORDER BY created_at ASC) FILTER (WHERE file_reference IS NOT NULL))[1] AS oldest_file_ref " +
            "FROM tbl_documents " +
            "WHERE hash = ?";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Khởi tạo DocumentDeduplicationHelperImpl.
     */
    public DocumentDeduplicationHelperImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DeduplicationQueryResult executeAggregateCheck(String hash, UUID departmentId) {
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

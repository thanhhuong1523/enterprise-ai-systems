package com.vccorp.eap.service.allocator.impl;

import com.vccorp.eap.service.allocator.BusinessCodeAllocator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Lớp thực thi của BusinessCodeAllocator.
 * Truy xuất cơ sở dữ liệu để lấy giá trị tiếp theo từ Sequence và format dạng ORIG_00000000.
 */
@Service
public class BusinessCodeAllocatorImpl implements BusinessCodeAllocator {

    /** Query SQL lấy mã tự tăng format pad 8 chữ số */
    private static final String SQL_ALLOCATE =
            "SELECT 'ORIG_' || lpad(nextval('doc_business_code_seq')::text, 8, '0')";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Khởi tạo BusinessCodeAllocatorImpl.
     */
    public BusinessCodeAllocatorImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String allocate() {
        return jdbcTemplate.queryForObject(SQL_ALLOCATE, String.class);
    }
}

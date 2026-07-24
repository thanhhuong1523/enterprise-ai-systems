package com.vccorp.eap.service.allocator;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Phân bổ mã nghiệp vụ {@code business_code} theo định dạng quy chuẩn Week 2
 * bằng cách gọi PostgreSQL sequence {@code doc_business_code_seq} (§8.2, ADR-010).
 * <p>
 * Sequence được khởi tạo trong V9 (START WITH 100000 INCREMENT BY 1).
 * Format: {@code ORIG_00XXXXXX} (8 chữ số có đệm 0).
 * Đảm bảo tính duy nhất tuyệt đối và nguyên tử ở tầng CSDL — không có đụng độ
 * UNIQUE constraint dù có 100 concurrent requests (ADR-010).
 */
@Service
public class BusinessCodeAllocator {

    private static final String SQL_ALLOCATE =
            "SELECT 'ORIG_' || lpad(nextval('doc_business_code_seq')::text, 8, '0')";

    private final JdbcTemplate jdbcTemplate;

    public BusinessCodeAllocator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Gọi {@code nextval('doc_business_code_seq')} và trả về mã nghiệp vụ đã định dạng.
     * Phải được gọi bên trong {@code TransactionTemplate.execute()} để sử dụng
     * đúng kết nối JDBC được ghim (§4.2).
     *
     * @return mã nghiệp vụ dạng {@code ORIG_00XXXXXX}
     */
    public String allocate() {
        return jdbcTemplate.queryForObject(SQL_ALLOCATE, String.class);
    }
}

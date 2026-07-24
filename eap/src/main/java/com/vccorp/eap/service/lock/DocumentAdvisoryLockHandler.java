package com.vccorp.eap.service.lock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Quản lý khóa cố vấn PostgreSQL transaction-level (§1.2, ADR-008, ADR-009).
 * <p>
 * Thực thi {@code pg_try_advisory_xact_lock} trực tiếp trên kết nối JDBC vật lý
 * thông qua {@code JdbcTemplate}.
 * Khóa được PostgreSQL tự động giải phóng khi giao dịch commit/rollback — không cần
 * phương thức mở khóa thủ công (ADR-008).
 */
@Component
public class DocumentAdvisoryLockHandler {

    private static final Logger log = LoggerFactory.getLogger(DocumentAdvisoryLockHandler.class);

    /**
     * Query 1 (§5): pg_try_advisory_xact_lock không chặn (non-blocking).
     * Phạm vi khóa: hashtextextended(concat(departmentId, ':', hash), 0) → BIGINT 64-bit.
     * Các phòng ban khác nhau tải cùng file cùng lúc có Lock ID khác nhau (ADR-009).
     */
    private static final String SQL_TRY_LOCK =
            "SELECT pg_try_advisory_xact_lock(hashtextextended(concat(?::text, ':', ?::text), 0))";

    private final JdbcTemplate jdbcTemplate;

    public DocumentAdvisoryLockHandler(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Thử lấy khóa cố vấn transaction-level không chặn trên kết nối JDBC.
     *
     * @param departmentId ID phòng ban của người dùng hiện tại
     * @param hash         SHA-256 hex hash của tệp tin
     * @return {@code true} nếu lấy được khóa, {@code false} nếu khóa đang bị giữ bởi luồng khác
     */
    public boolean tryAcquireLock(UUID departmentId, String hash) {
        if (departmentId == null) {
            throw new IllegalArgumentException("departmentId must not be null");
        }

        if (hash == null || hash.isBlank()) {
            throw new IllegalArgumentException("hash must not be blank");
        }

        Boolean acquired = jdbcTemplate.queryForObject(SQL_TRY_LOCK, Boolean.class, departmentId.toString(), hash);

        log.debug("Advisory lock attempt: dept={}, hash={}, acquired={}", departmentId, hash, acquired);
        return acquired != null && acquired;
    }
}

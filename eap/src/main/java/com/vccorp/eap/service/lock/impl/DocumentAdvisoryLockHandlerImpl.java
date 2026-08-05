package com.vccorp.eap.service.lock.impl;

import com.vccorp.eap.service.lock.DocumentAdvisoryLockHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Lớp thực thi của DocumentAdvisoryLockHandler.
 * Tận dụng hàm pg_try_advisory_xact_lock() của PostgreSQL để lấy Transaction-level Advisory Lock dựa trên hash.
 */
@Component
public class DocumentAdvisoryLockHandlerImpl implements DocumentAdvisoryLockHandler {

    private static final Logger log = LoggerFactory.getLogger(DocumentAdvisoryLockHandlerImpl.class);

    /** Query SQL gọi PostgreSQL advisory lock */
    private static final String SQL_TRY_LOCK =
            "SELECT pg_try_advisory_xact_lock(hashtextextended(concat(?::text, ':', ?::text), 0))";

    private final JdbcTemplate jdbcTemplate;

    /**
     * Khởi tạo DocumentAdvisoryLockHandlerImpl.
     */
    public DocumentAdvisoryLockHandlerImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * {@inheritDoc}
     */
    @Override
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

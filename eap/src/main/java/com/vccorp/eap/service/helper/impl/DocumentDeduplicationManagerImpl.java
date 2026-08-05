package com.vccorp.eap.service.helper.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.service.helper.DeduplicationQueryResult;
import com.vccorp.eap.service.helper.DocumentDeduplicationHelper;
import com.vccorp.eap.service.helper.DocumentDeduplicationManager;
import com.vccorp.eap.service.lock.DocumentAdvisoryLockHandler;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Lớp thực thi của DocumentDeduplicationManager.
 * Phối hợp cơ chế locking cơ sở dữ liệu và truy vấn gộp trùng lặp tài liệu để đảm bảo tính nhất quán (Double-Checked Locking).
 */
@Service
public class DocumentDeduplicationManagerImpl implements DocumentDeduplicationManager {

    private final DocumentAdvisoryLockHandler advisoryLockHandler;
    private final DocumentDeduplicationHelper deduplicationHelper;

    /**
     * Khởi tạo DocumentDeduplicationManagerImpl.
     */
    public DocumentDeduplicationManagerImpl(DocumentAdvisoryLockHandler advisoryLockHandler,
                                            DocumentDeduplicationHelper deduplicationHelper) {
        this.advisoryLockHandler = advisoryLockHandler;
        this.deduplicationHelper = deduplicationHelper;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void fastCheckDuplicate(String hash, UUID departmentId) {
        DeduplicationQueryResult result = deduplicationHelper.executeAggregateCheck(hash, departmentId);
        if (result.isHasActiveInDept()) {
            throw new BusinessException(ErrorCode.ERR_DUPLICATE_DOCUMENT);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean tryAcquireLock(UUID departmentId, String hash) {
        return advisoryLockHandler.tryAcquireLock(departmentId, hash);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DeduplicationQueryResult doubleCheckDuplicate(String hash, UUID departmentId) {
        DeduplicationQueryResult result = deduplicationHelper.executeAggregateCheck(hash, departmentId);
        if (result.isHasActiveInDept()) {
            throw new BusinessException(ErrorCode.ERR_DUPLICATE_DOCUMENT, "Tài liệu đã tồn tại trong phòng ban.");
        }
        return result;
    }
}

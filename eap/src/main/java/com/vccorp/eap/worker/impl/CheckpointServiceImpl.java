package com.vccorp.eap.worker.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.worker.CheckpointService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lớp thực thi của CheckpointService.
 * Cập nhật định kỳ chunkIndex hoàn thành vào cơ sở dữ liệu trong một Transaction độc lập (PROPAGATION_REQUIRES_NEW).
 */
@Service
public class CheckpointServiceImpl implements CheckpointService {

    private final DocumentRepository documentRepository;

    public CheckpointServiceImpl(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void commitCheckpoint(UUID id, String workerId, int chunkIndex) {
        int affected = documentRepository.updateCheckpoint(id, workerId, chunkIndex, LocalDateTime.now());
        if (affected == 0) {
            throw new BusinessException(
                ErrorCode.ERR_OWNERSHIP_LOST,
                "Worker " + workerId + " lost ownership of document " + id + ". Checkpoint commit rejected."
            );
        }
    }
}
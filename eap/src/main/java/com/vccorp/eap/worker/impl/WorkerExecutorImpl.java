package com.vccorp.eap.worker.impl;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.model.Document;
import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.worker.CheckpointService;
import com.vccorp.eap.worker.MockProcessingService;
import com.vccorp.eap.worker.WorkerExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.vccorp.eap.worker.util.DocumentPageCounter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Lớp thực thi của WorkerExecutor.
 * Chịu trách nhiệm toàn bộ tiến trình tải tệp vật lý, kiểm tra hash mã bảo mật, phân đoạn và xử lý phục hồi lỗi.
 */
@Service
public class WorkerExecutorImpl implements WorkerExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkerExecutorImpl.class);

    private static final int MAX_RETRIES = 5;
    private static final int MAX_CHUNK_RETRIES = 3;

    private final MockProcessingService mockProcessingService;
    private final CheckpointService checkpointService;
    private final DocumentRepository documentRepository;

    public WorkerExecutorImpl(MockProcessingService mockProcessingService,
                              CheckpointService checkpointService,
                              DocumentRepository documentRepository) {
        this.mockProcessingService = mockProcessingService;
        this.checkpointService = checkpointService;
        this.documentRepository = documentRepository;
    }

    @Override
    public void executeTask(UUID taskId, String workerId) {
        Document document = documentRepository.findById(taskId).orElse(null);
        if (document == null) {
            log.error("Document metadata not found in database for task: {}", taskId);
            return;
        }

        MDC.put("documentId", taskId.toString());
        MDC.put("workerId", workerId);
        MDC.put("retryCount", String.valueOf(document.getRetryCount() != null ? document.getRetryCount() : 0));

        try {
            log.info("Worker starts processing task {}", taskId);
            processTaskInternal(document, workerId);
            log.info("Successfully completed processing for document {}", taskId);
        } catch (BusinessException e) {
            log.error("Business error in worker execution for document {}", taskId, e);
            if (e.getErrorCode() != ErrorCode.ERR_OWNERSHIP_LOST &&
                e.getErrorCode() != ErrorCode.ERR_HASH_MISMATCH &&
                e.getErrorCode() != ErrorCode.ERR_DOCUMENT_NOT_FOUND) {
                handleTransientFailure(document, workerId);
            }
        } catch (IOException e) {
            log.error("I/O error during processing task {}", taskId, e);
            handleTransientFailure(document, workerId);
        } catch (InterruptedException e) {
            log.warn("Task {} processing was interrupted", taskId, e);
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            log.error("System error during processing task {}", taskId, t);
            handleTransientFailure(document, workerId);
        } finally {
            MDC.clear();
        }
    }

    /**
     * Phương thức thực thi nội bộ chứa toàn bộ logic kiểm tra mã băm, số phân đoạn và thực thi vòng lặp xử lý.
     */
    private void processTaskInternal(Document document, String workerId) throws IOException, InterruptedException {
        UUID taskId = document.getId();
        String filePath = document.getFileReference();

        // 1. Verify physical file exists
        if (filePath == null || !Files.exists(Path.of(filePath))) {
            documentRepository.markFailed(taskId, workerId, LocalDateTime.now());
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND, "Không tìm thấy tệp vật lý: " + filePath);
        }

        // 2. Security Check: Calculate and verify file hash integrity
        String expectedHash = document.getHash();
        String actualHash = calculateFileHash(filePath);

        if (expectedHash == null || !expectedHash.equalsIgnoreCase(actualHash)) {
            documentRepository.markFailed(taskId, workerId, LocalDateTime.now());
            throw new BusinessException(ErrorCode.ERR_HASH_MISMATCH, "Mã băm không khớp với tệp vật lý.");
        }

        // 3. Resolve and initialize total chunks
        int totalChunks = document.getTotalChunks() != null ? document.getTotalChunks() : 0;
        if (totalChunks <= 0) {
            totalChunks = DocumentPageCounter.countPages(filePath);
            int affected = documentRepository.updateTotalChunks(taskId, workerId, totalChunks, LocalDateTime.now());
            if (affected == 0) {
                throw new BusinessException(ErrorCode.ERR_OWNERSHIP_LOST, "Mất quyền sở hữu khi khởi tạo tổng số phân đoạn.");
            }
            log.info("Initialized total_chunks = {} for document {}", totalChunks, taskId);
        }

        int lastCompleted = document.getLastCompletedChunk() != null ? document.getLastCompletedChunk() : 0;

        // 4. Processing Loop
        for (int k = lastCompleted + 1; k <= totalChunks; k++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Task processing interrupted before starting chunk " + k);
            }

            // Process chunk with internal retry up to 3 times
            executeChunkWithRetry(taskId, k);

            // Commit checkpoint with ownership verification
            checkpointService.commitCheckpoint(taskId, workerId, k);
            log.debug("Committed checkpoint at chunk {} for document {}", k, taskId);
        }

        // 5. Complete task
        int affected = documentRepository.markCompleted(taskId, workerId, totalChunks, LocalDateTime.now());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.ERR_OWNERSHIP_LOST, "Mất quyền sở hữu khi đánh dấu hoàn thành tài liệu.");
        }
    }

    /**
     * Thực thi một phân đoạn cụ thể kèm cơ chế thử lại nội bộ (lên tới 3 lần).
     */
    private void executeChunkWithRetry(UUID taskId, int k) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_CHUNK_RETRIES; attempt++) {
            try {
                mockProcessingService.processChunk(taskId, k);
                return; // success
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw e;
            } catch (Exception e) {
                log.warn("Attempt {}/{} failed for chunk {} of task {}", attempt, MAX_CHUNK_RETRIES, k, taskId, e);
                if (attempt == MAX_CHUNK_RETRIES) {
                    throw new BusinessException(ErrorCode.ERR_SYSTEM_ERROR, "Thất bại xử lý phân đoạn " + k + " sau " + MAX_CHUNK_RETRIES + " lần thử.", e);
                }
                long baseDelay = 500L * attempt;
                long jitter = new java.util.Random().nextInt((int) (baseDelay * 0.4)) - (int) (baseDelay * 0.2); // +/- 20% Jitter
                Thread.sleep(Math.max(0L, baseDelay + jitter));
            }
        }
    }

    /**
     * Xử lý lỗi tạm thời (Transient Failure).
     * Tăng số lần thử lại (retry count), nếu vượt quá giới hạn thì chuyển task sang trạng thái FAILED.
     */
    private void handleTransientFailure(Document document, String workerId) {
        UUID taskId = document.getId();
        int currentRetry = document.getRetryCount() != null ? document.getRetryCount() : 0;
        int nextRetry = currentRetry + 1;
        if (nextRetry >= MAX_RETRIES) {
            log.error("Task {} has exceeded maximum retry attempts ({}). Marking task as FAILED.", taskId, nextRetry);
            int affected = documentRepository.markFailed(taskId, workerId, LocalDateTime.now());
            if (affected == 0) {
                throw new BusinessException(ErrorCode.ERR_OWNERSHIP_LOST, "Mất quyền sở hữu khi chuyển task sang FAILED.");
            }
        } else {
            log.warn("Task {} failed. Incrementing retry count to {} and releasing to READY state.", taskId, nextRetry);
            int affected = documentRepository.updateRetryCount(taskId, workerId, LocalDateTime.now());
            if (affected == 0) {
                throw new BusinessException(ErrorCode.ERR_OWNERSHIP_LOST, "Mất quyền sở hữu khi tăng số lần thử lại.");
            }
        }
    }

    /**
     * Tính toán mã băm SHA-256 thực tế của tệp vật lý.
     */
    private String calculateFileHash(String filePath) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-256 algorithm not available", e);
        }
        try (InputStream fis = Files.newInputStream(Path.of(filePath))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}

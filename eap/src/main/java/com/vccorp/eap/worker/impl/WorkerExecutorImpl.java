package com.vccorp.eap.worker.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.model.Document;
import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.service.document.DocumentTextExtractor;
import com.vccorp.eap.service.document.ParagraphChunker;
import com.vccorp.eap.worker.CheckpointService;
import com.vccorp.eap.worker.DocumentChunkProcessor;
import com.vccorp.eap.worker.WorkerExecutor;

/**
 * Lớp thực thi của WorkerExecutor.
 * Chịu trách nhiệm toàn bộ tiến trình tải tệp vật lý, kiểm tra hash mã bảo mật, phân đoạn và xử lý phục hồi lỗi.
 */
@Service
public class WorkerExecutorImpl implements WorkerExecutor {

    private static final Logger log = LoggerFactory.getLogger(WorkerExecutorImpl.class);

    private static final int MAX_RETRIES = 5;
    private static final int MAX_CHUNK_RETRIES = 3;

    private final DocumentChunkProcessor documentChunkProcessor;
    private final ParagraphChunker paragraphChunker;
    private final CheckpointService checkpointService;
    private final DocumentRepository documentRepository;
    private final DocumentTextExtractor documentTextExtractor;

    public WorkerExecutorImpl(DocumentChunkProcessor documentChunkProcessor,
                              ParagraphChunker paragraphChunker,
                              CheckpointService checkpointService,
                              DocumentRepository documentRepository,
                              DocumentTextExtractor documentTextExtractor) {
        this.documentChunkProcessor = documentChunkProcessor;
        this.paragraphChunker = paragraphChunker;
        this.checkpointService = checkpointService;
        this.documentRepository = documentRepository;
        this.documentTextExtractor = documentTextExtractor;
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
        } catch (Error err) {
            log.error("JVM Error during processing task {}", taskId, err);
            throw err;
        } catch (Exception e) {
            log.error("System error during processing task {}", taskId, e);
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
        Path filePath = document.getFileReference() != null ? Path.of(document.getFileReference()) : null;

        // 1. Verify physical file exists
        if (filePath == null || !Files.exists(filePath)) {
            documentRepository.markFailed(taskId, workerId, LocalDateTime.now());
            throw new BusinessException(ErrorCode.ERR_DOCUMENT_NOT_FOUND, "Không tìm thấy tệp vật lý: " + filePath);
        }

        // 2. Security Check: Calculate and verify file hash integrity
        String expectedHash = document.getHash();
        String actualHash;
        try (InputStream is = Files.newInputStream(filePath)) {
            actualHash = com.vccorp.eap.common.util.HashUtils.calculateSha256(is);
        }

        if (expectedHash == null || !expectedHash.equalsIgnoreCase(actualHash)) {
            documentRepository.markFailed(taskId, workerId, LocalDateTime.now());
            throw new BusinessException(ErrorCode.ERR_HASH_MISMATCH, "Mã băm không khớp với tệp vật lý.");
        }

        // 3. Extract text and split paragraphs — PDF uses page-aware path; other formats use existing path

        String mimeType;
        try (InputStream is = Files.newInputStream(filePath)) {
            byte[] header = is.readNBytes(256);
            mimeType = new org.apache.tika.Tika().detect(header);
        } catch (IOException e) {
            mimeType = "application/octet-stream";
        }


        List<com.vccorp.eap.dto.document.ChunkDraft> drafts;
        if ("application/pdf".equals(mimeType)) {
            java.util.List<com.vccorp.eap.dto.document.PageContent> pages = documentTextExtractor.extractTextByPage(filePath);
            drafts = paragraphChunker.chunkByPage(pages);
            log.info("PDF '{}': dùng page-aware chunking, {} trang → {} chunks", taskId, pages.size(), drafts.size());
        } else {
            String rawText = documentTextExtractor.extractText(filePath);
            drafts = paragraphChunker.chunkText(rawText);
            log.info("File '{}' ({}): dùng standard chunking → {} chunks", taskId, mimeType, drafts.size());
        }
        int computedTotalChunks = drafts.size();

        int totalChunks = document.getTotalChunks() != null ? document.getTotalChunks() : 0;
        if (totalChunks <= 0) {
            totalChunks = computedTotalChunks;
            int affected = documentRepository.updateTotalChunks(taskId, workerId, totalChunks, LocalDateTime.now());
            if (affected == 0) {
                throw new BusinessException(ErrorCode.ERR_OWNERSHIP_LOST, "Mất quyền sở hữu khi khởi tạo tổng số phân đoạn.");
            }
            log.info("Initialized total_chunks = {} for document {}", totalChunks, taskId);
        }

        int lastCompleted = document.getLastCompletedChunk() != null ? document.getLastCompletedChunk() : 0;
        int skippedChunks = 0;

        // 4. Processing Loop
        for (int k = lastCompleted + 1; k <= totalChunks; k++) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("Task processing interrupted before starting chunk " + k);
            }

            // Process chunk with internal retry up to 3 times
            com.vccorp.eap.dto.document.ChunkDraft draft = drafts.get(k - 1);
            boolean success = executeChunkWithRetry(taskId, k, draft.content(), draft.headingContext(), draft.pageNumber());
            if (!success) {
                skippedChunks++;
            }

            // Commit checkpoint with ownership verification
            checkpointService.commitCheckpoint(taskId, workerId, k);
            log.debug("Committed checkpoint at chunk {} for document {}", k, taskId);
        }

        // 5. Complete task
        int affected = documentRepository.markCompleted(taskId, workerId, totalChunks, skippedChunks, LocalDateTime.now());
        if (affected == 0) {
            throw new BusinessException(ErrorCode.ERR_OWNERSHIP_LOST, "Mất quyền sở hữu khi đánh dấu hoàn thành tài liệu.");
        }
    }

    private boolean executeChunkWithRetry(UUID taskId, int k, String content, String headingContext, int pageNumber) throws InterruptedException {
        for (int attempt = 1; attempt <= MAX_CHUNK_RETRIES; attempt++) {
            try {
                documentChunkProcessor.processChunk(taskId, k - 1, content, headingContext, pageNumber);
                return true; // success
            } catch (Exception e) {
                log.warn("Attempt {}/{} failed for chunk {} of task {}", attempt, MAX_CHUNK_RETRIES, k, taskId, e);
                if (attempt == MAX_CHUNK_RETRIES) {
                    log.error("Failed to process chunk {} of task {} after {} attempts. Skipping chunk.", k, taskId, MAX_CHUNK_RETRIES, e);
                    return false; // skip
                }
                long baseDelay = 500L * attempt;
                long jitter = new java.util.Random().nextInt((int) (baseDelay * 0.4)) - (int) (baseDelay * 0.2); // +/- 20% Jitter
                Thread.sleep(Math.max(0L, baseDelay + jitter));
            }
        }
        return false;
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


}

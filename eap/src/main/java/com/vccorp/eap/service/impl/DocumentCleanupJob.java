package com.vccorp.eap.service.impl;

import com.vccorp.eap.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Tiến trình dọn dẹp định kỳ chạy ngầm (Scheduled Cleanup Job) (ArchitectureDesign §7, DetailedDesign §6).
 * Quản lý và tối ưu hóa tài nguyên lưu trữ ngoài giờ cao điểm.
 */
@Component
public class DocumentCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(DocumentCleanupJob.class);

    private final DocumentRepository documentRepository;

    @Value("${eap.upload.dir:./eap-storage}")
    private String uploadDir;

    @Value("${eap.upload.temp-dir:./eap-storage/tmp}")
    private String tempUploadDir;

    @Value("${eap.cleanup.temp-expiration-ms:86400000}")
    private long tempExpirationMs; // mặc định 24h

    @Value("${eap.cleanup.orphan-grace-period-ms:600000}")
    private long orphanGracePeriodMs; // mặc định 10 phút

    private int tempDeletedCount = 0;

    public DocumentCleanupJob(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    /**
     * Kích hoạt dọn dẹp theo cấu hình cron expression.
     */
    @Scheduled(cron = "${eap.cleanup.cron:0 0 2 * * *}")
    public void cleanup() {
        log.info("Starting scheduled cleanup job...");
        long startTime = System.currentTimeMillis();

        try {
            cleanupTempFiles();
            cleanupOrphanFiles();
            cleanupDanglingMetadata();
            log.info("Scheduled cleanup job finished successfully in {} ms", System.currentTimeMillis() - startTime);
        } catch (IOException e) {
            log.error("I/O error occurred during scheduled cleanup job", e);
        } catch (DataAccessException e) {
            log.error("Database access error occurred during scheduled cleanup job", e);
        } catch (InvalidPathException e) {
            log.error("Invalid path error occurred during scheduled cleanup job", e);
        }
    }

    /**
     * Pha 1: Dọn dẹp tệp tạm thời hết hạn.
     */
    public void cleanupTempFiles() throws IOException {
        log.info("Starting Phase 1: Temporary File Cleanup");
        Path tempPath = Path.of(tempUploadDir).toAbsolutePath();
        if (!Files.exists(tempPath)) {
            log.info("Temporary directory does not exist, skipping: {}", tempPath);
            return;
        }

        Instant now = Instant.now();
        tempDeletedCount = 0;

        try (Stream<Path> files = Files.list(tempPath)) {
            files.filter(Files::isRegularFile).forEach(file -> deleteTempFileIfExpired(file, now));
        }

        log.info("Phase 1 finished. Deleted {} expired temp file(s).", tempDeletedCount);
    }

    private void deleteTempFileIfExpired(Path file, Instant now) {
        try {
            FileTime lastModified = Files.getLastModifiedTime(file);
            long ageMs = now.toEpochMilli() - lastModified.toMillis();
            if (ageMs > tempExpirationMs) {
                Files.deleteIfExists(file);
                log.debug("Deleted expired temp file: {}", file);
                tempDeletedCount++;
            }
        } catch (IOException e) {
            log.warn("Failed to check or delete temp file: {}", file, e);
        }
    }

    /**
     * Pha 2: Dọn dẹp tệp vật lý mồ côi.
     */
    public void cleanupOrphanFiles() throws IOException {
        log.info("Starting Phase 2: Orphan File Cleanup");
        Path storagePath = Path.of(uploadDir).toAbsolutePath();
        if (!Files.exists(storagePath)) {
            log.info("Storage directory does not exist, skipping: {}", storagePath);
            return;
        }

        Instant now = Instant.now();
        List<Path> candidateFiles = new ArrayList<>();
        List<String> candidateHashes = new ArrayList<>();

        try (Stream<Path> files = Files.list(storagePath)) {
            files.filter(Files::isRegularFile).forEach(file -> collectCandidateOrphan(file, now, candidateFiles, candidateHashes));
        }

        if (candidateHashes.isEmpty()) {
            log.info("Phase 2 finished. No candidate orphan files found.");
            return;
        }

        log.debug("Found {} candidate files for orphan check.", candidateHashes.size());

        List<String> orphanHashes = documentRepository.findOrphanHashes(candidateHashes.toArray(new String[0]));
        int deletedCount = 0;

        for (Path file : candidateFiles) {
            String hash = file.getFileName().toString();
            if (orphanHashes.contains(hash)) {
                try {
                    Files.deleteIfExists(file);
                    log.info("Deleted orphaned physical file: {}", file);
                    deletedCount++;
                } catch (IOException e) {
                    log.warn("Failed to delete orphaned physical file: {}", file, e);
                }
            }
        }

        log.info("Phase 2 finished. Deleted {} orphaned file(s).", deletedCount);
    }

    private void collectCandidateOrphan(Path file, Instant now, List<Path> candidateFiles, List<String> candidateHashes) {
        try {
            FileTime lastModified = Files.getLastModifiedTime(file);
            long ageMs = now.toEpochMilli() - lastModified.toMillis();
            if (ageMs > orphanGracePeriodMs) {
                candidateFiles.add(file);
                candidateHashes.add(file.getFileName().toString());
            }
        } catch (IOException e) {
            log.warn("Failed to inspect file age: {}", file, e);
        }
    }

    /**
     * Pha 3: Đối chiếu Metadata lỗi không tồn tại tệp vật lý (Dangling Metadata Check) (§6.5.2)
     */
    public void cleanupDanglingMetadata() {
        log.info("Starting Phase 3: Dangling Metadata Check");
        List<com.vccorp.eap.model.Document> documents = documentRepository.findDanglingMetadata();
        int failedCount = 0;

        for (com.vccorp.eap.model.Document doc : documents) {
            if (checkAndHandleDangling(doc)) {
                failedCount++;
            }
        }
        log.info("Phase 3 finished. Marked {} dangling metadata task(s) as FAILED.", failedCount);
    }

    private boolean checkAndHandleDangling(com.vccorp.eap.model.Document doc) {
        String fileRef = doc.getFileReference();
        if (fileRef == null || !Files.exists(Path.of(fileRef))) {
            log.warn("Security Alert: physical file missing for document {}. Setting status to FAILED.", doc.getId());
            int affected = documentRepository.forceMarkFailed(doc.getId(), LocalDateTime.now());
            return affected > 0;
        }
        return false;
    }
}

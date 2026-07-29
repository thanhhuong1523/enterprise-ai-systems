package com.vccorp.eap.service.impl;

import com.vccorp.eap.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
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
        int deletedCount = 0;

        try (Stream<Path> files = Files.list(tempPath)) {
            Iterable<Path> iterable = files::iterator;
            for (Path file : iterable) {
                if (Files.isRegularFile(file)) {
                    try {
                        FileTime lastModified = Files.getLastModifiedTime(file);
                        long ageMs = now.toEpochMilli() - lastModified.toMillis();
                        if (ageMs > tempExpirationMs) {
                            Files.deleteIfExists(file);
                            log.debug("Deleted expired temp file: {}", file);
                            deletedCount++;
                        }
                    } catch (IOException e) {
                        log.warn("Failed to check or delete temp file: {}", file, e);
                    }
                }
            }
        }

        log.info("Phase 1 finished. Deleted {} expired temp file(s).", deletedCount);
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
            Iterable<Path> iterable = files::iterator;
            for (Path file : iterable) {
                // Chỉ dọn dẹp các tệp trực tiếp trong thư mục chính thức (không quét thư mục con như tmp)
                if (Files.isRegularFile(file)) {
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
            }
        }

        if (candidateHashes.isEmpty()) {
            log.info("Phase 2 finished. No candidate orphan files found.");
            return;
        }

        log.debug("Found {} candidate files for orphan check.", candidateHashes.size());

        // Đối chiếu cơ sở dữ liệu để xác định các mã băm mồ côi
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
}

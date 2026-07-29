package com.vccorp.eap.service;

import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.service.impl.DocumentCleanupJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DocumentCleanupJobTest {

    @Mock
    private DocumentRepository documentRepository;

    private DocumentCleanupJob cleanupJob;

    @TempDir
    Path tempStorageDir;

    private Path tempUploadDir;

    @BeforeEach
    void setUp() throws IOException {
        tempUploadDir = tempStorageDir.resolve("tmp");
        Files.createDirectories(tempUploadDir);

        cleanupJob = new DocumentCleanupJob(documentRepository);

        // Inject configuration values
        ReflectionTestUtils.setField(cleanupJob, "uploadDir", tempStorageDir.toAbsolutePath().toString());
        ReflectionTestUtils.setField(cleanupJob, "tempUploadDir", tempUploadDir.toAbsolutePath().toString());
        ReflectionTestUtils.setField(cleanupJob, "tempExpirationMs", 1000L); // 1 second
        ReflectionTestUtils.setField(cleanupJob, "orphanGracePeriodMs", 2000L); // 2 seconds
    }

    @Test
    void cleanupTempFiles_Success() throws IOException, InterruptedException {
        // Create an expired temp file
        Path expiredFile = tempUploadDir.resolve("temp_expired");
        Files.createFile(expiredFile);
        Files.setLastModifiedTime(expiredFile, FileTime.from(Instant.now().minusMillis(2000)));

        // Create a non-expired temp file
        Path activeFile = tempUploadDir.resolve("temp_active");
        Files.createFile(activeFile);
        Files.setLastModifiedTime(activeFile, FileTime.from(Instant.now()));

        // Run
        cleanupJob.cleanupTempFiles();

        // Assert
        assertFalse(Files.exists(expiredFile), "Expired file should be deleted");
        assertTrue(Files.exists(activeFile), "Active file should not be deleted");
    }

    @Test
    void cleanupOrphanFiles_Success() throws IOException {
        // Create files in the main storage directory
        Path orphanFile = tempStorageDir.resolve("hash_orphan");
        Files.createFile(orphanFile);
        Files.setLastModifiedTime(orphanFile, FileTime.from(Instant.now().minusMillis(4000)));

        Path activeFile = tempStorageDir.resolve("hash_active");
        Files.createFile(activeFile);
        Files.setLastModifiedTime(activeFile, FileTime.from(Instant.now().minusMillis(4000)));

        Path youngFile = tempStorageDir.resolve("hash_young");
        Files.createFile(youngFile);
        Files.setLastModifiedTime(youngFile, FileTime.from(Instant.now())); // Young file (inside grace period)

        // Mock database check
        // Only return "hash_orphan" as orphan. "hash_active" has active metadata in DB, so it is NOT returned as orphan.
        when(documentRepository.findOrphanHashes(any(String[].class)))
                .thenReturn(List.of("hash_orphan"));

        // Run
        cleanupJob.cleanupOrphanFiles();

        // Assert
        assertFalse(Files.exists(orphanFile), "Orphan file should be deleted");
        assertTrue(Files.exists(activeFile), "Active file should not be deleted");
        assertTrue(Files.exists(youngFile), "Young file should not be checked or deleted due to grace period");
        verify(documentRepository).findOrphanHashes(any(String[].class));
    }
}

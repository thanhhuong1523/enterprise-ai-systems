package com.vccorp.eap.integration;

import com.vccorp.eap.dto.TaskClaimedResult;
import com.vccorp.eap.model.Document;
import com.vccorp.eap.recovery.RecoveryService;
import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.worker.CheckpointService;
import com.vccorp.eap.worker.MockProcessingService;
import com.vccorp.eap.worker.WorkerExecutor;
import com.vccorp.eap.worker.WorkerScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
public class DocumentProcessingIntegrationTest {

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private MockProcessingService mockProcessingService;

    @Autowired
    private CheckpointService checkpointService;

    @Autowired
    private WorkerExecutor workerExecutor;

    @Autowired
    private WorkerScheduler workerScheduler;

    @Autowired
    private ThreadPoolTaskExecutor workerTaskExecutor;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    private UUID taskId;
    private String hash;
    private Path tempFile;

    @BeforeEach
    void setUp() throws Exception {
        taskId = UUID.randomUUID();
        hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"; // Empty file SHA-256
        tempFile = Files.createTempFile("eap_test_", ".txt");
        // Ensure scheduler is stopped before test
        if (workerScheduler.isRunning()) {
            workerScheduler.stop();
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        Files.deleteIfExists(tempFile);
        if (workerScheduler.isRunning()) {
            workerScheduler.stop();
        }
    }

    @Test
    public void testRecoveryService_StartsScheduler() throws Exception {
        // Given
        when(documentRepository.resetProcessingTasksToReady(any(LocalDateTime.class))).thenReturn(3);

        RecoveryService recoveryService = new RecoveryService(documentRepository, workerScheduler, transactionManager);

        // When
        recoveryService.run(null);

        // Then
        verify(documentRepository).resetProcessingTasksToReady(any(LocalDateTime.class));
        assertTrue(workerScheduler.isRunning(), "WorkerScheduler must start after recovery runs.");
    }

    @Test
    public void testWorkerScheduler_ClaimsAndSubmitsTask() throws Exception {
        // Given
        String fileRef = tempFile.toAbsolutePath().toString();
        TaskClaimedResult claimedResult = new TaskClaimedResult(taskId, 0, 0, fileRef);
        when(documentRepository.claimTask(anyString(), any(LocalDateTime.class), anyInt()))
                .thenReturn(Optional.of(claimedResult))
                .thenReturn(Optional.empty()); // Next poll returns empty

        // Mock document retrieval
        Document doc = Document.builder()
                .id(taskId)
                .hash(hash)
                .fileReference(fileRef)
                .totalChunks(0)
                .retryCount(0)
                .lastCompletedChunk(0)
                .build();
        when(documentRepository.findById(taskId)).thenReturn(Optional.of(doc));

        // When
        workerScheduler.pollTasks();

        // Wait up to 2 seconds for worker thread executor pool tasks to finish
        long start = System.currentTimeMillis();
        while (workerTaskExecutor.getActiveCount() > 0 && (System.currentTimeMillis() - start) < 2000) {
            Thread.sleep(100);
        }

        // Then
        verify(documentRepository, atLeastOnce()).claimTask(anyString(), any(LocalDateTime.class), eq(5));
    }

    @Test
    public void testWorkerExecutor_SuccessLifecycle() throws Exception {
        // Given
        String fileRef = tempFile.toAbsolutePath().toString();
        Document doc = Document.builder()
                .id(taskId)
                .hash(hash)
                .fileReference(fileRef)
                .totalChunks(5)
                .retryCount(0)
                .lastCompletedChunk(2) // already finished chunk 1 & 2
                .build();
        when(documentRepository.findById(taskId)).thenReturn(Optional.of(doc));
        when(documentRepository.updateCheckpoint(eq(taskId), anyString(), anyInt(), any(LocalDateTime.class)))
                .thenReturn(1);
        when(documentRepository.markCompleted(eq(taskId), anyString(), eq(5), any(LocalDateTime.class)))
                .thenReturn(1);

        // When
        workerExecutor.executeTask(taskId, "test-worker-1");

        // Then
        // Should skip chunk 1 and 2, start processing chunk 3, 4, 5
        verify(mockProcessingService, times(1)).processChunk(taskId, 3);
        verify(mockProcessingService, times(1)).processChunk(taskId, 4);
        verify(mockProcessingService, times(1)).processChunk(taskId, 5);
        verify(mockProcessingService, never()).processChunk(taskId, 1);
        verify(mockProcessingService, never()).processChunk(taskId, 2);

        // Verify checkpoint committed for 3, 4, 5
        verify(documentRepository).updateCheckpoint(eq(taskId), eq("test-worker-1"), eq(3), any(LocalDateTime.class));
        verify(documentRepository).updateCheckpoint(eq(taskId), eq("test-worker-1"), eq(4), any(LocalDateTime.class));
        verify(documentRepository).updateCheckpoint(eq(taskId), eq("test-worker-1"), eq(5), any(LocalDateTime.class));

        // Verify marked complete
        verify(documentRepository).markCompleted(eq(taskId), eq("test-worker-1"), eq(5), any(LocalDateTime.class));
    }

    @Test
    public void testWorkerExecutor_HashMismatch_MarksFailed() throws Exception {
        // Given
        String fileRef = tempFile.toAbsolutePath().toString();
        Document doc = Document.builder()
                .id(taskId)
                .hash("wrong-expected-hash") // not matching empty file hash
                .fileReference(fileRef)
                .totalChunks(5)
                .retryCount(0)
                .lastCompletedChunk(0)
                .build();
        when(documentRepository.findById(taskId)).thenReturn(Optional.of(doc));
        when(documentRepository.markFailed(eq(taskId), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);

        // When
        workerExecutor.executeTask(taskId, "test-worker-1");

        // Then
        verify(mockProcessingService, never()).processChunk(any(UUID.class), anyInt());
        verify(documentRepository).markFailed(eq(taskId), eq("test-worker-1"), any(LocalDateTime.class));
    }

    @Test
    public void testWorkerExecutor_TransientFailure_IncrementsRetryCount() throws Exception {
        // Given
        String fileRef = tempFile.toAbsolutePath().toString();
        Document doc = Document.builder()
                .id(taskId)
                .hash(hash)
                .fileReference(fileRef)
                .totalChunks(5)
                .retryCount(2) // current retry count = 2
                .lastCompletedChunk(0)
                .build();
        when(documentRepository.findById(taskId)).thenReturn(Optional.of(doc));
        
        // Mock exception during processing
        doThrow(new RuntimeException("Transient connection issue"))
                .when(mockProcessingService).processChunk(eq(taskId), anyInt());

        when(documentRepository.updateRetryCount(eq(taskId), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);

        // When
        workerExecutor.executeTask(taskId, "test-worker-1");

        // Then
        // Chunk 1 retried 3 times internally, then fails
        verify(mockProcessingService, times(3)).processChunk(taskId, 1);
        verify(documentRepository).updateRetryCount(eq(taskId), eq("test-worker-1"), any(LocalDateTime.class));
        verify(documentRepository, never()).markFailed(any(UUID.class), anyString(), any(LocalDateTime.class));
    }

    @Test
    public void testWorkerExecutor_ExceedMaxRetries_MarksFailed() throws Exception {
        // Given
        String fileRef = tempFile.toAbsolutePath().toString();
        Document doc = Document.builder()
                .id(taskId)
                .hash(hash)
                .fileReference(fileRef)
                .totalChunks(5)
                .retryCount(4) // current retry count = 4, next attempt would be 5
                .lastCompletedChunk(0)
                .build();
        when(documentRepository.findById(taskId)).thenReturn(Optional.of(doc));
        
        doThrow(new RuntimeException("Permanent processing error"))
                .when(mockProcessingService).processChunk(eq(taskId), anyInt());

        when(documentRepository.markFailed(eq(taskId), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);

        // When
        workerExecutor.executeTask(taskId, "test-worker-1");

        // Then
        verify(documentRepository).markFailed(eq(taskId), eq("test-worker-1"), any(LocalDateTime.class));
        verify(documentRepository, never()).updateRetryCount(any(UUID.class), anyString(), any(LocalDateTime.class));
    }
}

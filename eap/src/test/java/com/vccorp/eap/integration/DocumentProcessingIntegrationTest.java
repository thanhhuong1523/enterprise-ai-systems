package com.vccorp.eap.integration;

import com.vccorp.eap.dto.TaskClaimedResult;
import com.vccorp.eap.model.Document;
import com.vccorp.eap.recovery.RecoveryService;
import com.vccorp.eap.repository.DocumentRepository;
import com.vccorp.eap.service.DocumentTextExtractor;
import com.vccorp.eap.worker.CheckpointService;
import com.vccorp.eap.worker.DocumentChunkProcessor;
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

@SpringBootTest(properties = "eap.chunking.min-paragraph-length=0")
public class DocumentProcessingIntegrationTest {

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private DocumentChunkProcessor documentChunkProcessor;

    @MockBean
    private DocumentTextExtractor documentTextExtractor;

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
        // Mock default successful text extraction
        Mockito.lenient().when(documentTextExtractor.extractText(any(Path.class)))
                .thenReturn("Paragraph 1.\n\nParagraph 2.\n\nParagraph 3.\n\nParagraph 4.\n\nParagraph 5.");
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

        RecoveryService recoveryService = new RecoveryService(documentRepository, workerScheduler, transactionManager, true);

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
        when(documentRepository.markCompleted(eq(taskId), anyString(), eq(5), eq(0), any(LocalDateTime.class)))
                .thenReturn(1);

        // When
        workerExecutor.executeTask(taskId, "test-worker-1");

        // Then
        // Should skip chunk index 0 and 1, start processing chunk index 2, 3, 4
        verify(documentChunkProcessor, times(1)).processChunk(eq(taskId), eq(2), anyString(), anyString(), anyInt());
        verify(documentChunkProcessor, times(1)).processChunk(eq(taskId), eq(3), anyString(), anyString(), anyInt());
        verify(documentChunkProcessor, times(1)).processChunk(eq(taskId), eq(4), anyString(), anyString(), anyInt());
        verify(documentChunkProcessor, never()).processChunk(eq(taskId), eq(0), anyString(), anyString(), anyInt());
        verify(documentChunkProcessor, never()).processChunk(eq(taskId), eq(1), anyString(), anyString(), anyInt());

        // Verify checkpoint committed for 3, 4, 5
        verify(documentRepository).updateCheckpoint(eq(taskId), eq("test-worker-1"), eq(3), any(LocalDateTime.class));
        verify(documentRepository).updateCheckpoint(eq(taskId), eq("test-worker-1"), eq(4), any(LocalDateTime.class));
        verify(documentRepository).updateCheckpoint(eq(taskId), eq("test-worker-1"), eq(5), any(LocalDateTime.class));

        // Verify marked complete
        verify(documentRepository).markCompleted(eq(taskId), eq("test-worker-1"), eq(5), eq(0), any(LocalDateTime.class));
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
        verify(documentChunkProcessor, never()).processChunk(any(UUID.class), anyInt(), anyString(), anyString(), anyInt());
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
        
        // Mock exception during text extraction (document level transient failure)
        when(documentTextExtractor.extractText(any(Path.class)))
                .thenThrow(new RuntimeException("Transient extraction failure"));

        when(documentRepository.updateRetryCount(eq(taskId), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);

        // When
        workerExecutor.executeTask(taskId, "test-worker-1");

        // Then
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
        
        // Mock exception during text extraction (document level permanent failure)
        when(documentTextExtractor.extractText(any(Path.class)))
                .thenThrow(new RuntimeException("Permanent extraction failure"));

        when(documentRepository.markFailed(eq(taskId), anyString(), any(LocalDateTime.class)))
                .thenReturn(1);

        // When
        workerExecutor.executeTask(taskId, "test-worker-1");

        // Then
        verify(documentRepository).markFailed(eq(taskId), eq("test-worker-1"), any(LocalDateTime.class));
        verify(documentRepository, never()).updateRetryCount(any(UUID.class), anyString(), any(LocalDateTime.class));
    }

    @Test
    public void testWorkerExecutor_ChunkFailure_SkipsChunkAndCompletes() throws Exception {
        // Given
        String fileRef = tempFile.toAbsolutePath().toString();
        Document doc = Document.builder()
                .id(taskId)
                .hash(hash)
                .fileReference(fileRef)
                .totalChunks(5)
                .retryCount(0)
                .lastCompletedChunk(0)
                .build();
        when(documentRepository.findById(taskId)).thenReturn(Optional.of(doc));
        when(documentRepository.updateCheckpoint(eq(taskId), anyString(), anyInt(), any(LocalDateTime.class)))
                .thenReturn(1);
        
        doThrow(new RuntimeException("Chunk persistence failed"))
                .when(documentChunkProcessor).processChunk(eq(taskId), eq(1), anyString(), anyString(), anyInt());

        when(documentRepository.markCompleted(eq(taskId), anyString(), eq(5), eq(1), any(LocalDateTime.class)))
                .thenReturn(1);

        // When
        workerExecutor.executeTask(taskId, "test-worker-1");

        // Then
        // Verify other chunks were processed successfully
        verify(documentChunkProcessor, times(1)).processChunk(eq(taskId), eq(0), anyString(), anyString(), anyInt());
        verify(documentChunkProcessor, times(3)).processChunk(eq(taskId), eq(1), anyString(), anyString(), anyInt()); // Failed chunk retried 3 times
        verify(documentChunkProcessor, times(1)).processChunk(eq(taskId), eq(2), anyString(), anyString(), anyInt());
        verify(documentChunkProcessor, times(1)).processChunk(eq(taskId), eq(3), anyString(), anyString(), anyInt());
        verify(documentChunkProcessor, times(1)).processChunk(eq(taskId), eq(4), anyString(), anyString(), anyInt());

        // Verify checkpoint committed for all 5 chunks (since we still checkpoint skipped chunks)
        verify(documentRepository, times(5)).updateCheckpoint(eq(taskId), eq("test-worker-1"), anyInt(), any(LocalDateTime.class));

        // Verify marked complete with 1 skipped chunk
        verify(documentRepository).markCompleted(eq(taskId), eq("test-worker-1"), eq(5), eq(1), any(LocalDateTime.class));
    }
}

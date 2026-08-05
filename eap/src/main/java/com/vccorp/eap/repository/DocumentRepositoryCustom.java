package com.vccorp.eap.repository;

import com.vccorp.eap.dto.TaskClaimedResult;
import com.vccorp.eap.model.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepositoryCustom {
    Optional<TaskClaimedResult> claimTask(String workerId, LocalDateTime now, int maxRetry);
    int updateTotalChunks(UUID id, String workerId, int totalChunks, LocalDateTime now);
    int updateCheckpoint(UUID id, String workerId, int chunkIndex, LocalDateTime now);
    int updateRetryCount(UUID id, String workerId, LocalDateTime now);
    int markCompleted(UUID id, String workerId, int totalChunks, LocalDateTime now);
    int markFailed(UUID id, String workerId, LocalDateTime now);
    int forceMarkFailed(UUID id, LocalDateTime now);
    int resetProcessingTasksToReady(LocalDateTime now);
    int releaseTask(UUID id, String workerId, LocalDateTime now);
    List<Document> findDanglingMetadata();
}

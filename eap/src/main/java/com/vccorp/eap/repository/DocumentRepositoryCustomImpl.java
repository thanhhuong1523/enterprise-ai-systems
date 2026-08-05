package com.vccorp.eap.repository;

import com.vccorp.eap.dto.TaskClaimedResult;
import com.vccorp.eap.model.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DocumentRepositoryCustomImpl implements DocumentRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

    public DocumentRepositoryCustomImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<TaskClaimedResult> claimTask(String workerId, LocalDateTime now, int maxRetry) {
        String sql = "WITH claimed_task AS (" +
                "    SELECT id " +
                "    FROM tbl_documents " +
                "    WHERE status = 'READY' " +
                "      AND deleted_at IS NULL " +
                "      AND retry_count < ? " +
                "      AND (" +
                "          retry_count = 0 " +
                "          OR updated_at + (INTERVAL '1 second' * (10 * power(2, retry_count) * (0.8 + 0.4 * random()))) <= ?" +
                "      ) " +
                "    ORDER BY created_at ASC " +
                "    LIMIT 1 " +
                "    FOR UPDATE SKIP LOCKED " +
                ") " +
                "UPDATE tbl_documents " +
                "SET status = 'PROCESSING', " +
                "    worker_id = ?, " +
                "    updated_at = ? " +
                "FROM claimed_task " +
                "WHERE tbl_documents.id = claimed_task.id " +
                "RETURNING tbl_documents.id, tbl_documents.last_completed_chunk, tbl_documents.total_chunks, tbl_documents.file_reference";

        Timestamp timestamp = Timestamp.valueOf(now);
        List<TaskClaimedResult> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            UUID id = (UUID) rs.getObject("id");
            int lastCompletedChunk = rs.getInt("last_completed_chunk");
            int totalChunks = rs.getInt("total_chunks");
            String fileReference = rs.getString("file_reference");
            return new TaskClaimedResult(id, lastCompletedChunk, totalChunks, fileReference);
        }, maxRetry, timestamp, workerId, timestamp);

        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public int updateTotalChunks(UUID id, String workerId, int totalChunks, LocalDateTime now) {
        String sql = "UPDATE tbl_documents " +
                "SET total_chunks = ?, " +
                "    updated_at = ? " +
                "WHERE id = ? " +
                "  AND worker_id = ? " +
                "  AND status = 'PROCESSING' " +
                "  AND deleted_at IS NULL";
        return jdbcTemplate.update(sql, totalChunks, Timestamp.valueOf(now), id, workerId);
    }

    @Override
    public int updateCheckpoint(UUID id, String workerId, int chunkIndex, LocalDateTime now) {
        String sql = "UPDATE tbl_documents " +
                "SET last_completed_chunk = ?, " +
                "    updated_at = ? " +
                "WHERE id = ? " +
                "  AND worker_id = ? " +
                "  AND status = 'PROCESSING' " +
                "  AND last_completed_chunk < ? " +
                "  AND ? <= total_chunks " +
                "  AND deleted_at IS NULL";
        return jdbcTemplate.update(sql, chunkIndex, Timestamp.valueOf(now), id, workerId, chunkIndex, chunkIndex);
    }

    @Override
    public int updateRetryCount(UUID id, String workerId, LocalDateTime now) {
        String sql = "UPDATE tbl_documents " +
                "SET status = 'READY', " +
                "    worker_id = NULL, " +
                "    retry_count = retry_count + 1, " +
                "    updated_at = ? " +
                "WHERE id = ? " +
                "  AND worker_id = ? " +
                "  AND status = 'PROCESSING' " +
                "  AND deleted_at IS NULL";
        return jdbcTemplate.update(sql, Timestamp.valueOf(now), id, workerId);
    }

    @Override
    public int markCompleted(UUID id, String workerId, int totalChunks, LocalDateTime now) {
        String sql = "UPDATE tbl_documents " +
                "SET status = 'COMPLETED', " +
                "    worker_id = NULL, " +
                "    last_completed_chunk = ?, " +
                "    updated_at = ? " +
                "WHERE id = ? " +
                "  AND worker_id = ? " +
                "  AND status = 'PROCESSING' " +
                "  AND deleted_at IS NULL";
        return jdbcTemplate.update(sql, totalChunks, Timestamp.valueOf(now), id, workerId);
    }

    @Override
    public int markFailed(UUID id, String workerId, LocalDateTime now) {
        String sql = "UPDATE tbl_documents " +
                "SET status = 'FAILED', " +
                "    worker_id = NULL, " +
                "    updated_at = ? " +
                "WHERE id = ? " +
                "  AND worker_id = ? " +
                "  AND status = 'PROCESSING' " +
                "  AND deleted_at IS NULL";
        return jdbcTemplate.update(sql, Timestamp.valueOf(now), id, workerId);
    }

    @Override
    public int forceMarkFailed(UUID id, LocalDateTime now) {
        String sql = "UPDATE tbl_documents " +
                "SET status = 'FAILED', " +
                "    worker_id = NULL, " +
                "    updated_at = ? " +
                "WHERE id = ? " +
                "  AND deleted_at IS NULL";
        return jdbcTemplate.update(sql, Timestamp.valueOf(now), id);
    }

    @Override
    public int resetProcessingTasksToReady(LocalDateTime now) {
        String sql = "UPDATE tbl_documents " +
                "SET status = 'READY', " +
                "    worker_id = NULL, " +
                "    retry_count = retry_count + 1, " +
                "    updated_at = ? " +
                "WHERE status = 'PROCESSING' " +
                "  AND deleted_at IS NULL";
        return jdbcTemplate.update(sql, Timestamp.valueOf(now));
    }

    @Override
    public int releaseTask(UUID id, String workerId, LocalDateTime now) {
        String sql = "UPDATE tbl_documents " +
                "SET status = 'READY', " +
                "    worker_id = NULL, " +
                "    updated_at = ? " +
                "WHERE id = ? " +
                "  AND worker_id = ? " +
                "  AND status = 'PROCESSING' " +
                "  AND deleted_at IS NULL";
        return jdbcTemplate.update(sql, Timestamp.valueOf(now), id, workerId);
    }

    @Override
    public List<Document> findDanglingMetadata() {
        String sql = "SELECT id, file_reference, hash " +
                "FROM tbl_documents " +
                "WHERE status IN ('READY', 'PROCESSING') " +
                "  AND file_reference IS NOT NULL " +
                "  AND deleted_at IS NULL";
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Document doc = new Document();
            doc.setId((UUID) rs.getObject("id"));
            doc.setFileReference(rs.getString("file_reference"));
            doc.setHash(rs.getString("hash"));
            return doc;
        });
    }
}

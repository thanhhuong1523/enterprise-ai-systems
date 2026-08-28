package com.vccorp.eap.repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.dto.document.ChunkSearchResult;
import com.vccorp.eap.dto.search.SearchContext;

@Repository
public class ChunkRepositoryCustomImpl implements ChunkRepositoryCustom {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${eap.rag.top-k:3}")
    private int topK;

    public ChunkRepositoryCustomImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<ChunkSearchResult> searchWithAuth(SearchContext context) {
        UUID boardDeptId = context.boardDeptId();

        String queryVectorStr = buildVectorString(context.queryVector());
        
        String metadataFilterJson = null;
        if (context.metadataFilter() != null && !context.metadataFilter().isEmpty()) {
            try {
                metadataFilterJson = objectMapper.writeValueAsString(context.metadataFilter());
            } catch (JsonProcessingException e) {
                // ignore
            }
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("queryVector", queryVectorStr);
        params.addValue("userDeptId", context.userDeptId());
        params.addValue("boardDeptId", boardDeptId);
        params.addValue("metadataFilter", metadataFilterJson);
        params.addValue("limit", topK);

        // SQL UNION ALL:
        // Nhánh 1: Tài liệu do phòng ban người dùng sở hữu trực tiếp
        // Nhánh 2: Tài liệu được chia sẻ hợp lệ qua Alias tới phòng ban của người dùng
        String sql = 
            "SELECT * FROM (" +
            "  SELECT c.id AS chunk_id, c.content AS chunk_content, c.metadata AS chunk_metadata, " +
            "         d.id AS document_id, d.title AS display_title, d.business_code AS document_business_code, " +
            "         1.0 - (c.embedding <=> CAST(:queryVector AS vector)) AS similarity_score " +
            "  FROM tbl_chunks c " +
            "  JOIN tbl_documents d ON c.document_id = d.id " +
            "  WHERE d.parent_id IS NULL " +
            "    AND d.status = 'COMPLETED' " +
            "    AND d.deleted_at IS NULL " +
            "    AND d.owner_department_id = :userDeptId " +
            "    AND (d.owner_department_id <> :boardDeptId OR :userDeptId = :boardDeptId) " +
            "    AND (CAST(:metadataFilter AS jsonb) IS NULL OR c.metadata @> CAST(:metadataFilter AS jsonb)) " +
            "  ORDER BY c.embedding <=> CAST(:queryVector AS vector) ASC " +
            "  LIMIT :limit " +
            ") AS own_docs " +
            "UNION ALL " +
            "(" +
            "  SELECT c.id AS chunk_id, c.content AS chunk_content, c.metadata AS chunk_metadata, " +
            "         d.id AS document_id, d.title AS display_title, d.business_code AS document_business_code, " +
            "         1.0 - (c.embedding <=> CAST(:queryVector AS vector)) AS similarity_score " +
            "  FROM tbl_chunks c " +
            "  JOIN tbl_documents d ON c.document_id = d.id " +
            "  JOIN tbl_documents a ON a.parent_id = d.id AND a.owner_department_id = :userDeptId AND a.deleted_at IS NULL " +
            "  WHERE d.parent_id IS NULL " +
            "    AND d.status = 'COMPLETED' " +
            "    AND d.deleted_at IS NULL " +
            "    AND (d.owner_department_id <> :boardDeptId OR :userDeptId = :boardDeptId) " +
            "    AND (CAST(:metadataFilter AS jsonb) IS NULL OR c.metadata @> CAST(:metadataFilter AS jsonb)) " +
            "  ORDER BY c.embedding <=> CAST(:queryVector AS vector) ASC " +
            "  LIMIT :limit " +
            ") " +
            "ORDER BY similarity_score DESC " +
            "LIMIT :limit";

        return jdbcTemplate.query(sql, params, (rs, rowNum) -> {
            Map<String, Object> metaMap = Collections.emptyMap();
            String metaStr = rs.getString("chunk_metadata");
            if (metaStr != null && !metaStr.isEmpty()) {
                try {
                    metaMap = objectMapper.readValue(metaStr, Map.class);
                } catch (JsonProcessingException e) {
                    // ignore
                }
            }
            return new ChunkSearchResult(
                UUID.fromString(rs.getString("chunk_id")),
                rs.getString("chunk_content"),
                metaMap,
                UUID.fromString(rs.getString("document_id")),
                rs.getString("display_title"),
                rs.getString("document_business_code"),
                rs.getDouble("similarity_score")
            );
        });
    }

    @Override
    public boolean existsCandidateWithMetadata(Map<String, Object> metadataFilter, UUID userDeptId, UUID boardDeptId) {
        String metadataFilterJson;
        try {
            metadataFilterJson = objectMapper.writeValueAsString(metadataFilter);
        } catch (JsonProcessingException e) {
            return true; // fail-open
        }

        String sql =
            "SELECT EXISTS (" +
            "  SELECT 1 FROM tbl_chunks c " +
            "  JOIN tbl_documents d ON c.document_id = d.id " +
            "  WHERE d.parent_id IS NULL " +
            "    AND d.status = 'COMPLETED' " +
            "    AND d.deleted_at IS NULL " +
            "    AND (d.owner_department_id = :userDeptId " +
            "         OR EXISTS (" +
            "             SELECT 1 FROM tbl_documents a " +
            "             WHERE a.parent_id = d.id " +
            "               AND a.owner_department_id = :userDeptId " +
            "               AND a.deleted_at IS NULL)) " +
            "    AND (d.owner_department_id <> :boardDeptId OR :userDeptId = :boardDeptId) " +
            "    AND c.metadata @> CAST(:metadataFilter AS jsonb) " +
            "  LIMIT 1" +
            ")";

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("userDeptId", userDeptId);
        params.addValue("boardDeptId", boardDeptId);
        params.addValue("metadataFilter", metadataFilterJson);

        Boolean result = jdbcTemplate.queryForObject(sql, params, Boolean.class);
        return Boolean.TRUE.equals(result);
    }

    private String buildVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            sb.append(embedding[i]);
            if (i < embedding.length - 1) sb.append(",");
        }
        sb.append("]");
        return sb.toString();
    }
}

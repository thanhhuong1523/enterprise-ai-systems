package com.vccorp.eap.repository;

import com.vccorp.eap.dto.ChunkSearchResult;
import com.vccorp.eap.dto.SearchContext;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ChunkRepositoryCustom {
    List<ChunkSearchResult> searchWithAuth(SearchContext context);
    boolean existsCandidateWithMetadata(Map<String, Object> metadataFilter, UUID userDeptId, UUID boardDeptId);
}

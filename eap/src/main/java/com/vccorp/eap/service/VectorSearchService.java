package com.vccorp.eap.service;

import com.vccorp.eap.dto.ChunkSearchResult;
import com.vccorp.eap.dto.SearchContext;
import java.util.List;

public interface VectorSearchService {
    List<ChunkSearchResult> searchWithAuth(SearchContext context);
}

package com.vccorp.eap.service.search;

import com.vccorp.eap.dto.document.ChunkSearchResult;
import com.vccorp.eap.dto.search.SearchContext;
import java.util.List;

public interface VectorSearchService {
    List<ChunkSearchResult> searchWithAuth(SearchContext context);
}

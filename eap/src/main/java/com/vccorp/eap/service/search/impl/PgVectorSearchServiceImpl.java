package com.vccorp.eap.service.search.impl;

import com.vccorp.eap.dto.document.ChunkSearchResult;
import com.vccorp.eap.dto.search.SearchContext;
import com.vccorp.eap.repository.ChunkRepository;
import com.vccorp.eap.service.search.VectorSearchService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PgVectorSearchServiceImpl implements VectorSearchService {

    private final ChunkRepository chunkRepository;

    public PgVectorSearchServiceImpl(ChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Override
    public List<ChunkSearchResult> searchWithAuth(SearchContext context) {
        return chunkRepository.searchWithAuth(context);
    }
}

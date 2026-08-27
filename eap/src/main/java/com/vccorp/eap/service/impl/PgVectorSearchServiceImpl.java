package com.vccorp.eap.service.impl;

import com.vccorp.eap.dto.ChunkSearchResult;
import com.vccorp.eap.dto.SearchContext;
import com.vccorp.eap.repository.ChunkRepository;
import com.vccorp.eap.service.VectorSearchService;
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

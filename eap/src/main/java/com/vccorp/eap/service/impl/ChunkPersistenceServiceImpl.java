package com.vccorp.eap.service.impl;

import com.vccorp.eap.model.Chunk;
import com.vccorp.eap.repository.ChunkRepository;
import com.vccorp.eap.service.ChunkPersistenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Service
public class ChunkPersistenceServiceImpl implements ChunkPersistenceService {

    private final ChunkRepository chunkRepository;

    public ChunkPersistenceServiceImpl(ChunkRepository chunkRepository) {
        this.chunkRepository = chunkRepository;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistChunk(UUID documentId, int chunkIndex, String content, float[] embedding, Map<String, Object> metadata) {
        UUID chunkId = UUID.nameUUIDFromBytes((documentId.toString() + "_" + chunkIndex).getBytes());
        Chunk chunk = new Chunk(chunkId, documentId, chunkIndex, content, embedding, metadata);
        chunkRepository.save(chunk);
    }
}

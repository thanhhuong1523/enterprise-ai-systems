package com.vccorp.eap.service;

import java.util.Map;
import java.util.UUID;

public interface ChunkPersistenceService {
    void persistChunk(UUID documentId, int chunkIndex, String content, float[] embedding, Map<String, Object> metadata);
}

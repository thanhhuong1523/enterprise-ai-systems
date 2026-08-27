package com.vccorp.eap.dto;

import java.util.Map;
import java.util.UUID;

public record ChunkSearchResult(
    UUID chunkId,
    String content,
    Map<String, Object> metadata,
    UUID documentId,
    String displayTitle,
    String businessCode,
    double similarityScore
) {}

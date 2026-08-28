package com.vccorp.eap.dto.document;

import java.util.UUID;

public record TaskClaimedResult(
    UUID id,
    int lastCompletedChunk,
    int totalChunks,
    String fileReference
) {}

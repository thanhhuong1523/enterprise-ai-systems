package com.vccorp.eap.dto;

import java.util.UUID;

public record TaskClaimedResult(
    UUID id,
    int lastCompletedChunk,
    int totalChunks,
    String fileReference
) {}

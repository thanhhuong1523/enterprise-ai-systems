package com.vccorp.eap.dto;

import java.util.List;

public record RagChatResponse(
    String response,
    List<ChunkResultDto> chunks
) {
}

package com.vccorp.eap.dto.search;

import java.util.List;
import com.vccorp.eap.dto.document.ChunkResultDto;

public record RagChatResponse(
    String response,
    List<ChunkResultDto> chunks
) {
}

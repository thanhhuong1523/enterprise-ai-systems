package com.vccorp.eap.service.document;

import com.vccorp.eap.dto.document.ChunkDraft;
import com.vccorp.eap.dto.document.PageContent;
import java.util.List;

public interface ParagraphChunker {
    /** Phân mảnh văn bản thô (không biết số trang). pageNumber sẽ là 0 với mọi ChunkDraft. */
    List<ChunkDraft> chunkText(String rawText);

    /**
     * Phân mảnh danh sách trang PDF, giữ nguyên số trang nguồn trong mỗi {@link ChunkDraft}.
     */
    List<ChunkDraft> chunkByPage(List<PageContent> pages);
}


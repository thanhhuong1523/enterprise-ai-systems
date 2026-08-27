package com.vccorp.eap.worker;

import java.util.UUID;

public interface DocumentChunkProcessor {
    /**
     * Xử lý một phân đoạn tài liệu: sinh embedding, trích xuất metadata qua LLM và lưu vào DB.
     *
     * @param documentId     ID của tài liệu
     * @param chunkIndex     vị trí chunk (0-based)
     * @param content        nội dung văn bản của chunk
     * @param headingContext chuỗi tiêu đề phân cấp
     * @param pageNumber     số trang PDF gốc (0 nếu không xác định)
     */
    void processChunk(UUID documentId, int chunkIndex, String content, String headingContext, int pageNumber);
}


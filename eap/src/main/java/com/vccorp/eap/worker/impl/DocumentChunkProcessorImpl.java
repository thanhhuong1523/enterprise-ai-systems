package com.vccorp.eap.worker.impl;

import com.vccorp.eap.worker.DocumentChunkProcessor;
import com.vccorp.eap.repository.DocumentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import com.vccorp.eap.service.document.ChunkPersistenceService;
import com.vccorp.eap.service.document.LlmMetadataExtractorService;
import com.vccorp.eap.service.embedding.EmbeddingService;

@Service
public class DocumentChunkProcessorImpl implements DocumentChunkProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentChunkProcessorImpl.class);

    private final DocumentRepository documentRepository;
    private final EmbeddingService embeddingService;
    private final LlmMetadataExtractorService llmMetadataExtractorService;
    private final ChunkPersistenceService chunkPersistenceService;

    public DocumentChunkProcessorImpl(
            DocumentRepository documentRepository,
            EmbeddingService embeddingService,
            LlmMetadataExtractorService llmMetadataExtractorService,
            ChunkPersistenceService chunkPersistenceService) {
        this.documentRepository = documentRepository;
        this.embeddingService = embeddingService;
        this.llmMetadataExtractorService = llmMetadataExtractorService;
        this.chunkPersistenceService = chunkPersistenceService;
    }

    @Override
    public void processChunk(UUID documentId, int chunkIndex, String content, String headingContext, int pageNumber) {
        if (!documentRepository.existsById(documentId)) {
            throw new IllegalArgumentException("Không tìm thấy document ID: " + documentId);
        }

        // 1. Sinh vector nhúng và trích xuất Metadata qua LLM trực tiếp từ nội dung nhận được
        float[] embedding = embeddingService.embedText(content);
        Map<String, Object> metadata = llmMetadataExtractorService.extractChunkMetadata(content, headingContext);

        if (metadata == null) {
            metadata = new java.util.HashMap<>();
        } else {
            metadata = new java.util.HashMap<>(metadata);
        }

        // Fallback bảo vệ đảm bảo metadata không bao giờ null hoặc rỗng
        metadata.putIfAbsent("doc_type", "other");
        metadata.putIfAbsent("topics", java.util.List.of("general_info"));

        // Chèn thông tin trích dẫn phân cấp tiêu đề (citation_headings)
        if (headingContext != null && !headingContext.isBlank()) {
            java.util.List<String> citationHeadings = java.util.Arrays.stream(headingContext.split("\\s*>\\s*"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (!citationHeadings.isEmpty()) {
                metadata.put("citation_headings", citationHeadings);
            }
        }

        // Ghi số trang PDF vào metadata nếu có (pageNumber > 0)
        if (pageNumber > 0) {
            metadata.put("page_number", pageNumber);
        }

        // 2. Persist chunk trong một transaction REQUIRES_NEW riêng biệt
        chunkPersistenceService.persistChunk(documentId, chunkIndex, content, embedding, metadata);
        log.info("Đã sinh vector, trích xuất metadata và lưu phân đoạn {} (trang {}) cho tài liệu ID {}",
                chunkIndex, pageNumber > 0 ? pageNumber : "N/A", documentId);
    }
}

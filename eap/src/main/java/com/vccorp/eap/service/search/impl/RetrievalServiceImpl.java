package com.vccorp.eap.service.search.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vccorp.eap.common.error.ErrorCode;
import com.vccorp.eap.common.exception.BusinessException;
import com.vccorp.eap.dto.document.ChunkResultDto;
import com.vccorp.eap.dto.document.ChunkSearchResult;
import com.vccorp.eap.dto.search.RagChatRequest;
import com.vccorp.eap.dto.search.RagChatResponse;
import com.vccorp.eap.dto.search.SearchContext;
import com.vccorp.eap.enums.Role;
import com.vccorp.eap.model.User;
import com.vccorp.eap.repository.ChunkRepository;
import com.vccorp.eap.repository.DepartmentRepository;
import com.vccorp.eap.service.document.LlmMetadataExtractorService;
import com.vccorp.eap.service.embedding.EmbeddingService;
import com.vccorp.eap.service.helper.LlmClient;
import com.vccorp.eap.service.search.RagAnswerGeneratorService;
import com.vccorp.eap.service.search.RetrievalService;
import com.vccorp.eap.service.search.VectorSearchService;

@Service
public class RetrievalServiceImpl implements RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalServiceImpl.class);

    private final LlmMetadataExtractorService llmMetadataExtractorService;
    private final EmbeddingService embeddingService;
    private final VectorSearchService vectorSearchService;
    private final TaskExecutor taskExecutor;
    private final DepartmentRepository departmentRepository;
    private final ChunkRepository chunkRepository;
    private final RagAnswerGeneratorService ragAnswerGeneratorService;
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    @Value("${eap.rag.similarity-threshold:0.0}")
    private double similarityThreshold;

    public RetrievalServiceImpl(
            LlmMetadataExtractorService llmMetadataExtractorService,
            EmbeddingService embeddingService,
            VectorSearchService vectorSearchService,
            @Qualifier("asyncRetrievalExecutor") TaskExecutor taskExecutor,
            DepartmentRepository departmentRepository,
            ChunkRepository chunkRepository,
            RagAnswerGeneratorService ragAnswerGeneratorService,
            LlmClient llmClient,
            ObjectMapper objectMapper) {
        this.llmMetadataExtractorService = llmMetadataExtractorService;
        this.embeddingService = embeddingService;
        this.vectorSearchService = vectorSearchService;
        this.taskExecutor = taskExecutor;
        this.departmentRepository = departmentRepository;
        this.chunkRepository = chunkRepository;
        this.ragAnswerGeneratorService = ragAnswerGeneratorService;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public RagChatResponse search(RagChatRequest request, User currentUser) {
        if (request == null || request.message() == null || request.message().trim().isEmpty()) {
            throw new BusinessException(ErrorCode.ERR_INVALID_REQUEST, "Tin nhắn không được để trống.");
        }

        String message = request.message().trim();

        // 1. Phân quyền: Kiểm tra an toàn currentUser và block SYSTEM_ADMIN
        if (currentUser == null) {
            throw new BusinessException(ErrorCode.ERR_UNAUTHENTICATED);
        }
        if (currentUser.getRole() == Role.SYSTEM_ADMIN) {
            throw new BusinessException(ErrorCode.ERR_FORBIDDEN_ROLE,
                    "Quản trị viên hệ thống không được phép tìm kiếm tài liệu.");
        }

        // 2. Chạy song song CompletableFuture: Lấy LLM metadata + embed câu hỏi
        CompletableFuture<Map<String, Object>> llmFuture = CompletableFuture.supplyAsync(
                () -> llmMetadataExtractorService.extractMetadata(message), taskExecutor)
                .exceptionally(ex -> {
                    log.warn("Lấy LLM metadata thất bại, tự động bỏ qua bộ lọc: {}", ex.getMessage());
                    return Collections.emptyMap();
                });
        
        CompletableFuture<float[]> embedFuture = CompletableFuture.supplyAsync(
                () -> embeddingService.embedText(message), taskExecutor);

        // Chờ cả 2 kết thúc
        CompletableFuture.allOf(llmFuture, embedFuture).join();

        Map<String, Object> metadataFilter = llmFuture.getNow(Collections.emptyMap());
        log.info("Kết quả trích xuất metadata từ LLM cho câu hỏi '{}': {}", message, metadataFilter);
        float[] queryVector = embedFuture.getNow(null);

        UUID boardDeptId = departmentRepository.findByCode("BOARD")
                .map(d -> d.getId())
                .orElse(UUID.fromString("00000000-0000-0000-0000-000000000000"));

        // 3. Short-circuit: 
        // Nếu trích xuất được metadata nhưng không có ứng viên nào khớp trong DB -> Trả về không tìm thấy ngay.
        if (metadataFilter != null && !metadataFilter.isEmpty()) {
            boolean hasCandidate = chunkRepository.existsCandidateWithMetadata(
                    metadataFilter, currentUser.getDepartmentId(), boardDeptId);
            if (!hasCandidate) {
                log.info("Short-circuit: không có phân đoạn nào khớp metadata filter cho user {}",
                        currentUser.getUsername());
                return new RagChatResponse("Không tìm thấy kết quả phù hợp.", Collections.emptyList());
            }
        }

        // 4. Đóng gói context và tìm kiếm tại Database (sau khi LLM filter, xếp hạng theo vector cosine)
        SearchContext context = new SearchContext(message, queryVector, metadataFilter, currentUser.getDepartmentId(), boardDeptId);
        List<ChunkSearchResult> searchResults = vectorSearchService.searchWithAuth(context);

        // 5. Map sang typed DTO cho API Response (áp dụng threshold điểm cosine)
        List<ChunkResultDto> chunks = searchResults.stream()
                .filter(res -> res.similarityScore() >= similarityThreshold)
                .map(res -> {
                    Object rawPage = res.metadata().get("page_number");
                    Integer pageNumber = null;
                    if (rawPage instanceof Integer i) {
                        pageNumber = i;
                    } else if (rawPage instanceof Number n) {
                        pageNumber = n.intValue();
                    }
                    String citation = "[" + res.displayTitle()
                            + (pageNumber != null ? ", Trang " + pageNumber : "")
                            + "]";
                    return new ChunkResultDto(
                            res.content(),
                            res.similarityScore(),
                            res.displayTitle(),
                            res.businessCode(),
                            res.metadata(),
                            pageNumber,
                            citation
                    );
                }).collect(Collectors.toList());

        if (chunks.isEmpty()) {
            log.info("Không tìm thấy kết quả nào cho user {}: {}", currentUser.getUsername(), message);
            return new RagChatResponse("Rất tiếc, thông tin này không có trong các tài liệu bạn có quyền truy cập.", Collections.emptyList());
        }

        log.info("Tìm thấy {} kết quả cho user {}: {}. Đang gọi LLM tổng hợp câu trả lời...", chunks.size(), currentUser.getUsername(), message);
        String generatedResponse = ragAnswerGeneratorService.generateAnswer(message, chunks);
        return new RagChatResponse(generatedResponse, chunks);
    }
}
